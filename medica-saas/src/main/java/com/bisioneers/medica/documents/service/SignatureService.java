package com.bisioneers.medica.documents.service;

import com.bisioneers.medica.documents.domain.*;
import com.bisioneers.medica.documents.security.SignatureTokenService;
import com.bisioneers.medica.medical.storage.MediaStorageService;
import com.bisioneers.medica.notification.NotificationService;
import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import com.bisioneers.medica.tenant.domain.TenantEntity;
import com.bisioneers.medica.tenant.domain.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Orquestador del ciclo de vida de firmas electrónicas.
 *
 * Flujos soportados:
 *  - IN_PERSON: staff inicia, staff confirma con firma del paciente (mismo dispositivo)
 *  - REMOTE: staff genera link, paciente firma desde su dispositivo
 *
 * Reglas de negocio:
 *  - Documento debe estar en READY_TO_SIGN (con pdfStorageKey populado)
 *  - Solo puede haber 1 SignatureRequest PENDING por documento a la vez
 *  - Para REMOTE: paciente debe confirmar su número de documento
 *  - Máximo 5 intentos fallidos de cédula antes de invalidar el link
 *  - Tokens son opacos (256 bits aleatorios), almacenados como SHA-256
 */
@Service
public class SignatureService {

    private static final Logger log = LoggerFactory.getLogger(SignatureService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final SignatureRequestRepository requestRepo;
    private final PatientDocumentRepository documentRepo;
    private final PatientRepository patientRepo;
    private final TenantRepository tenantRepo;
    private final SignatureTokenService tokenService;
    private final SignedPdfRenderer pdfRenderer;
    private final MediaStorageService storageService;
    private final NotificationService notificationService;

    @Value("${app.public-base-url:http://localhost:4200}")
    private String publicBaseUrl;

    public SignatureService(SignatureRequestRepository requestRepo,
                              PatientDocumentRepository documentRepo,
                              PatientRepository patientRepo,
                              TenantRepository tenantRepo,
                              SignatureTokenService tokenService,
                              SignedPdfRenderer pdfRenderer,
                              MediaStorageService storageService,
                              NotificationService notificationService) {
        this.requestRepo = requestRepo;
        this.documentRepo = documentRepo;
        this.patientRepo = patientRepo;
        this.tenantRepo = tenantRepo;
        this.tokenService = tokenService;
        this.pdfRenderer = pdfRenderer;
        this.storageService = storageService;
        this.notificationService = notificationService;
    }

    // ════════════════════════════════════════════════════════════════
    // FLUJO A — IN-PERSON (firma en mismo dispositivo del staff)
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public SignatureRequestEntity startInPersonSignature(UUID tenantId, UUID documentId,
                                                          UUID staffUserId) {
        getDocumentReadyToSign(tenantId, documentId);
        cancelExistingPending(tenantId, documentId);

        SignatureRequestEntity req = new SignatureRequestEntity();
        req.setTenantId(tenantId);
        req.setPatientDocumentId(documentId);
        req.setMethod(SignatureMethod.IN_PERSON);
        req.setStatus(SignatureRequestStatus.PENDING);
        req.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        req.setCreatedByStaffUserId(staffUserId);

        return requestRepo.save(req);
    }

    @Transactional
    public PatientDocumentEntity confirmInPersonSignature(
            UUID tenantId, UUID documentId, UUID signatureRequestId,
            String signatureDataUrl, String signerName, String signerDocument,
            String clientIp, String userAgent, UUID witnessStaffUserId
    ) {
        SignatureRequestEntity req = requestRepo.findByIdAndTenantId(signatureRequestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud de firma no encontrada"));

        if (!req.getPatientDocumentId().equals(documentId)) {
            throw new IllegalArgumentException("La solicitud no corresponde al documento");
        }
        if (req.getMethod() != SignatureMethod.IN_PERSON) {
            throw new IllegalStateException("Esta solicitud no es de tipo IN_PERSON");
        }
        if (req.getStatus() != SignatureRequestStatus.PENDING) {
            throw new IllegalStateException("La solicitud ya no está pendiente: " + req.getStatus());
        }
        if (req.getExpiresAt().isBefore(Instant.now())) {
            req.setStatus(SignatureRequestStatus.EXPIRED);
            requestRepo.save(req);
            throw new IllegalStateException("La solicitud ha expirado");
        }

        return finalizeSignature(req, signatureDataUrl, signerName, signerDocument,
                clientIp, userAgent, witnessStaffUserId);
    }

    // ════════════════════════════════════════════════════════════════
    // FLUJO B — REMOTE (link enviado al paciente)
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public RemoteSignatureCreationResult startRemoteSignature(
            UUID tenantId, UUID documentId, UUID staffUserId,
            RemoteDeliveryChannel channel, String customMessage
    ) {
        PatientDocumentEntity doc = getDocumentReadyToSign(tenantId, documentId);
        PatientEntity patient = patientRepo.findById(doc.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));
        TenantEntity tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));

        cancelExistingPending(tenantId, documentId);

        // Generar token y crear request
        String token = tokenService.generateToken();
        String tokenHash = tokenService.hashToken(token);

        int hoursValid = tenant.getSignatureLinkHours();
        if (hoursValid < 1 || hoursValid > 168) hoursValid = 24;

        SignatureRequestEntity req = new SignatureRequestEntity();
        req.setTenantId(tenantId);
        req.setPatientDocumentId(documentId);
        req.setMethod(SignatureMethod.REMOTE);
        req.setStatus(SignatureRequestStatus.PENDING);
        req.setTokenHash(tokenHash);
        req.setExpiresAt(Instant.now().plus(hoursValid, ChronoUnit.HOURS));
        req.setCreatedByStaffUserId(staffUserId);
        req.setDeliveryChannel(channel);

        // URL completa que se entrega al paciente
        String signUrl = publicBaseUrl + "/sign/" + token;

        // Intentar entrega usando NotificationService.send() (rutea por formato)
        String deliveryTarget = null;
        boolean deliverySuccessful = false;
        String deliveryError = null;

        try {
            String msg = buildPatientMessage(tenant, doc, signUrl, customMessage, hoursValid);

            switch (channel) {
                case WHATSAPP -> {
                    if (patient.getPhone() == null || patient.getPhone().isBlank()) {
                        throw new IllegalArgumentException("El paciente no tiene teléfono registrado");
                    }
                    deliveryTarget = patient.getPhone();
                    // send() rutea por formato del destino: phone → WhatsApp
                    deliverySuccessful = notificationService.send(deliveryTarget, null, msg);
                    if (!deliverySuccessful) {
                        deliveryError = "El servicio de WhatsApp rechazó el envío (verifica credenciales Twilio).";
                    }
                }
                case EMAIL -> {
                    if (patient.getEmail() == null || patient.getEmail().isBlank()) {
                        throw new IllegalArgumentException("El paciente no tiene email registrado");
                    }
                    deliveryTarget = patient.getEmail();
                    String subject = "Firma requerida: " + doc.getTitle();
                    // send() rutea por formato del destino: email → SMTP
                    deliverySuccessful = notificationService.send(deliveryTarget, subject, msg);
                    if (!deliverySuccessful) {
                        deliveryError = "El servicio de email rechazó el envío (verifica SMTP).";
                    }
                }
                case MANUAL -> {
                    deliveryTarget = "(copiado manualmente)";
                    deliverySuccessful = true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to deliver signature link via {}: {}", channel, e.getMessage());
            deliveryError = e.getMessage();
            deliverySuccessful = false;
        }

        req.setDeliveryTarget(deliveryTarget);
        req.setDeliverySuccessful(deliverySuccessful);
        req.setDeliveryError(deliveryError);

        req = requestRepo.save(req);

        return new RemoteSignatureCreationResult(req, token, signUrl);
    }

    @Transactional
    public SignatureRequestEntity cancelRequest(UUID tenantId, UUID requestId) {
        SignatureRequestEntity req = requestRepo.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        if (req.getStatus() != SignatureRequestStatus.PENDING) {
            throw new IllegalStateException("Solo se pueden cancelar solicitudes PENDING");
        }
        req.setStatus(SignatureRequestStatus.CANCELLED);
        return requestRepo.save(req);
    }

    @Transactional(readOnly = true)
    public List<SignatureRequestEntity> listByDocument(UUID tenantId, UUID documentId) {
        return requestRepo.findByTenantIdAndPatientDocumentIdOrderByIdDesc(tenantId, documentId);
    }

    // ────── Acceso público por token ──────

    @Transactional(readOnly = true)
    public SignatureRequestEntity resolveTokenForView(String token) {
        String hash = tokenService.hashToken(token);
        SignatureRequestEntity req = requestRepo
                .findByTokenHashAndStatus(hash, SignatureRequestStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("Link inválido o ya utilizado"));

        if (req.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("El link ha expirado");
        }
        return req;
    }

    @Transactional
    public PatientDocumentEntity confirmRemoteSignature(
            String token, String signatureDataUrl, String confirmDocumentNumber,
            String clientIp, String userAgent
    ) {
        String hash = tokenService.hashToken(token);
        SignatureRequestEntity req = requestRepo
                .findByTokenHashAndStatus(hash, SignatureRequestStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("Link inválido o ya utilizado"));

        if (req.getExpiresAt().isBefore(Instant.now())) {
            req.setStatus(SignatureRequestStatus.EXPIRED);
            requestRepo.save(req);
            throw new IllegalStateException("El link ha expirado");
        }

        PatientDocumentEntity doc = documentRepo.findById(req.getPatientDocumentId())
                .orElseThrow(() -> new IllegalStateException("Documento no encontrado"));

        PatientEntity patient = patientRepo.findById(doc.getPatientId())
                .orElseThrow(() -> new IllegalStateException("Paciente no encontrado"));

        if (!normalize(patient.getDocumentNumber()).equals(normalize(confirmDocumentNumber))) {
            req.setFailedAttempts(req.getFailedAttempts() + 1);
            if (req.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                req.setStatus(SignatureRequestStatus.CANCELLED);
                requestRepo.save(req);
                throw new IllegalStateException(
                        "Demasiados intentos fallidos. El link ha sido invalidado.");
            }
            requestRepo.save(req);
            int remaining = MAX_FAILED_ATTEMPTS - req.getFailedAttempts();
            throw new IllegalArgumentException(
                    "Número de documento incorrecto. Te quedan " + remaining + " intento(s).");
        }

        return finalizeSignature(req,
                signatureDataUrl,
                patient.getFullName(),
                patient.getDocumentNumber(),
                clientIp,
                userAgent,
                null);
    }

    // ════════════════════════════════════════════════════════════════
    // CORE: finalizar firma (común a ambos flujos)
    // ════════════════════════════════════════════════════════════════

    private PatientDocumentEntity finalizeSignature(
            SignatureRequestEntity req,
            String signatureDataUrl,
            String signerName,
            String signerDocument,
            String clientIp,
            String userAgent,
            UUID witnessStaffUserId
    ) {
        UUID tenantId = req.getTenantId();
        PatientDocumentEntity doc = documentRepo.findById(req.getPatientDocumentId())
                .orElseThrow(() -> new IllegalStateException("Documento no encontrado"));

        if (!"READY_TO_SIGN".equals(doc.getStatus())) {
            throw new IllegalStateException("El documento no está en estado READY_TO_SIGN");
        }

        TenantEntity tenant = tenantRepo.findById(tenantId).orElse(null);
        String tenantName = tenant != null ? tenant.getDisplayName() : "Medica";

        Instant signedAt = Instant.now();

        byte[] signedPdfBytes;
        try (InputStream basePdfStream = storageService.load(doc.getPdfStorageKey())) {
            signedPdfBytes = pdfRenderer.embedSignature(
                    basePdfStream,
                    signatureDataUrl,
                    signerName,
                    signerDocument,
                    clientIp,
                    userAgent,
                    signedAt,
                    tenantName
            );
        } catch (java.io.IOException e) {
            throw new RuntimeException("Error leyendo el PDF base", e);
        }

        String integrityHash = sha256Hex(signedPdfBytes);

        UUID signedDocId = UUID.randomUUID();
        String signedKey = storageService.storeBytes(
                tenantId, doc.getPatientId(), signedDocId,
                signedPdfBytes, "application/pdf",
                "signed-" + doc.getId() + ".pdf"
        );

        doc.setSignedPdfStorageKey(signedKey);
        doc.setStatus("SIGNED");
        doc.setSignedAt(signedAt);
        doc.setSignerName(signerName);
        doc.setSignerDocument(signerDocument);
        doc.setSignerIp(clientIp);
        doc.setSignerUserAgent(truncate(userAgent, 500));
        doc.setIntegrityHash(integrityHash);
        doc.setSignatureMethod("DIGITAL");
        if (witnessStaffUserId != null) {
            doc.setWitnessStaffUserId(witnessStaffUserId);
        }
        documentRepo.save(doc);

        req.setStatus(SignatureRequestStatus.SIGNED);
        req.setSignedAt(signedAt);
        if (witnessStaffUserId != null) {
            req.setWitnessStaffUserId(witnessStaffUserId);
        }
        requestRepo.save(req);

        log.info("Document signed: doc={}, method={}, hash={}",
                doc.getId(), req.getMethod(), integrityHash.substring(0, 12) + "...");

        return doc;
    }

    // ════════════════════════════════════════════════════════════════
    // Scheduled cleanup
    // ════════════════════════════════════════════════════════════════

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void expireOldRequests() {
        List<SignatureRequestEntity> expired = requestRepo.findExpired(Instant.now());
        for (SignatureRequestEntity req : expired) {
            req.setStatus(SignatureRequestStatus.EXPIRED);
            requestRepo.save(req);
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} pending signature requests", expired.size());
        }
    }

    // ────── Helpers ──────

    private PatientDocumentEntity getDocumentReadyToSign(UUID tenantId, UUID documentId) {
        PatientDocumentEntity doc = documentRepo.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado"));
        if (!"READY_TO_SIGN".equals(doc.getStatus())) {
            throw new IllegalStateException(
                    "Solo se pueden iniciar firmas para documentos en READY_TO_SIGN. " +
                    "Estado actual: " + doc.getStatus());
        }
        if (doc.getPdfStorageKey() == null) {
            throw new IllegalStateException("El documento no tiene PDF generado");
        }
        return doc;
    }

    private void cancelExistingPending(UUID tenantId, UUID documentId) {
        requestRepo.findPendingByDocument(tenantId, documentId).ifPresent(existing -> {
            existing.setStatus(SignatureRequestStatus.CANCELLED);
            requestRepo.save(existing);
            log.info("Cancelled existing PENDING request {} before creating new one", existing.getId());
        });
    }

    private String buildPatientMessage(TenantEntity tenant, PatientDocumentEntity doc,
                                         String signUrl, String customMessage, int hours) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hola, ").append(tenant.getDisplayName())
          .append(" requiere tu firma electronica para el siguiente documento:\n\n");
        sb.append("\"").append(doc.getTitle()).append("\"\n\n");

        if (customMessage != null && !customMessage.isBlank()) {
            sb.append(customMessage).append("\n\n");
        }

        sb.append("Por favor abre este enlace seguro para firmar:\n");
        sb.append(signUrl).append("\n\n");
        sb.append("El enlace expira en ").append(hours).append(" horas.\n");
        sb.append("Necesitaras tu numero de cedula/pasaporte para confirmar tu identidad.");

        return sb.toString();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", "").toUpperCase();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    /**
     * Resultado de crear SignatureRequest REMOTE.
     * El token plano solo está disponible una vez, en este DTO.
     */
    public record RemoteSignatureCreationResult(
            SignatureRequestEntity request,
            String tokenPlain,
            String signUrl
    ) {}
}
