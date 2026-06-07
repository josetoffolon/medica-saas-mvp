package com.bisioneers.medica.documents.service;

import com.bisioneers.medica.documents.domain.PatientDocumentEntity;
import com.bisioneers.medica.documents.domain.PatientDocumentRepository;
import com.bisioneers.medica.medical.storage.MediaStorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Maneja la subida de documentos firmados físicamente (escaneados).
 *
 * Flujo:
 *   1. Validar que el documento está en READY_TO_SIGN
 *   2. Validar el archivo (tipo permitido + tamaño)
 *   3. Si es imagen → convertir a PDF (un solo formato de salida)
 *   4. Calcular SHA-256
 *   5. Subir a R2 como signed PDF
 *   6. Marcar documento como SIGNED con signatureMethod = SCANNED
 *
 * Tipos permitidos: application/pdf, image/jpeg, image/png
 * Tamaño máximo: parametrizable (app.scanned-upload.max-size-mb, default 10)
 */
@Service
public class ScannedDocumentService {

	private static final Logger log = LoggerFactory.getLogger(ScannedDocumentService.class);

	private static final Set<String> ALLOWED_TYPES = Set.of(
			"application/pdf", "image/jpeg", "image/jpg", "image/png");

	private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Panama");

	private final PatientDocumentRepository documentRepo;
	private final MediaStorageService storageService;

	/** Tamaño máximo del archivo en MB. Parametrizable. Default 10. */
	@Value("${app.scanned-upload.max-size-mb:10}")
	private int maxSizeMb;

	public ScannedDocumentService(PatientDocumentRepository documentRepo,
			MediaStorageService storageService) {
		this.documentRepo = documentRepo;
		this.storageService = storageService;
	}

	/**
	 * Sube un documento escaneado y marca el PatientDocument como SIGNED.
	 *
	 * @param witnessStaffUserId staff autenticado que registra la subida
	 */
	@Transactional
	public PatientDocumentEntity uploadScanned(
			UUID tenantId, UUID documentId, MultipartFile file,
			String signerName, String signerDocument, String signedDate,
			String clientIp, String userAgent, UUID witnessStaffUserId
			) {
		PatientDocumentEntity doc = documentRepo.findByIdAndTenantId(documentId, tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Documento no encontrado"));

		if (!"READY_TO_SIGN".equals(doc.getStatus())) {
			throw new IllegalStateException(
					"Solo se pueden subir documentos escaneados en estado READY_TO_SIGN. " +
							"Estado actual: " + doc.getStatus());
		}

		validateFile(file);

		// Determinar timestamp de firma
		Instant signedAt = parseSignedDate(signedDate);

		// Obtener bytes finales (PDF). Si es imagen, convertir.
		byte[] pdfBytes;
		String contentType = file.getContentType();
		try {
			if (contentType != null && contentType.startsWith("image/")) {
				pdfBytes = convertImageToPdf(file.getBytes());
			} else {
				pdfBytes = file.getBytes();
			}
		} catch (IOException e) {
			throw new RuntimeException("Error procesando el archivo", e);
		}

		// Hash de integridad
		String integrityHash = sha256Hex(pdfBytes);

		// Subir a R2
		UUID signedDocId = UUID.randomUUID();
		String signedKey = storageService.storeBytes(
				tenantId, doc.getPatientId(), signedDocId,
				pdfBytes, "application/pdf",
				"scanned-" + doc.getId() + ".pdf"
				);

		// Actualizar documento
		doc.setSignedPdfStorageKey(signedKey);
		doc.setStatus("SIGNED");
		doc.setSignedAt(signedAt);
		doc.setSignerName(signerName);
		doc.setSignerDocument(signerDocument);
		doc.setSignerIp(clientIp);
		doc.setSignerUserAgent(truncate(userAgent, 500));
		doc.setIntegrityHash(integrityHash);
		doc.setSignatureMethod("SCANNED");
		if (witnessStaffUserId != null) {
			doc.setWitnessStaffUserId(witnessStaffUserId);
		}
		documentRepo.save(doc);

		log.info("Scanned document uploaded: doc={}, originalType={}, hash={}",
				doc.getId(), contentType, integrityHash.substring(0, 12) + "...");

		return doc;
	}

	// ─── Validación ────────────────────────────────────────────

	private void validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("No se recibió ningún archivo");
		}

		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
			throw new IllegalArgumentException(
					"Tipo de archivo no permitido. Solo se aceptan PDF, JPG y PNG. " +
							"Recibido: " + contentType);
		}

		long maxBytes = (long) maxSizeMb * 1024 * 1024;
		if (file.getSize() > maxBytes) {
			throw new IllegalArgumentException(
					"El archivo excede el tamaño máximo de " + maxSizeMb + " MB");
		}
	}

	// ─── Conversión imagen → PDF ───────────────────────────────

	/**
	 * Convierte una imagen (JPG/PNG) a un PDF de una página.
	 * La imagen se escala para caber en una página Letter manteniendo el ratio.
	 */
	private byte[] convertImageToPdf(byte[] imageBytes) {
		try (PDDocument document = new PDDocument()) {
			PDImageXObject image = PDImageXObject.createFromByteArray(
					document, imageBytes, "scanned");

			PDPage page = new PDPage(PDRectangle.LETTER);
			document.addPage(page);

			float pageWidth = PDRectangle.LETTER.getWidth();
			float pageHeight = PDRectangle.LETTER.getHeight();
			float margin = 20f;
			float maxWidth = pageWidth - 2 * margin;
			float maxHeight = pageHeight - 2 * margin;

			float imgWidth = image.getWidth();
			float imgHeight = image.getHeight();

			// Escalar para caber manteniendo ratio
			float scale = Math.min(maxWidth / imgWidth, maxHeight / imgHeight);
			if (scale > 1f) scale = 1f; // no agrandar imágenes pequeñas

			float finalWidth = imgWidth * scale;
			float finalHeight = imgHeight * scale;
			float x = (pageWidth - finalWidth) / 2f;
			float y = (pageHeight - finalHeight) / 2f;

			try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
				cs.drawImage(image, x, y, finalWidth, finalHeight);
			}

			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				document.save(out);
				return out.toByteArray();
			}

		} catch (IOException e) {
			log.error("Failed to convert image to PDF: {}", e.getMessage(), e);
			throw new RuntimeException("No se pudo convertir la imagen a PDF", e);
		}
	}

	// ─── Helpers ───────────────────────────────────────────────

	private Instant parseSignedDate(String signedDate) {
		if (signedDate == null || signedDate.isBlank()) {
			return Instant.now();
		}
		try {
			LocalDate date = LocalDate.parse(signedDate.trim());
			return date.atStartOfDay(DEFAULT_ZONE).toInstant();
		} catch (Exception e) {
			log.warn("Invalid signedDate '{}', using now()", signedDate);
			return Instant.now();
		}
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
}
