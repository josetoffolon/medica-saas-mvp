package com.bisioneers.medica.consent.service;

import com.bisioneers.medica.consent.domain.*;
import com.bisioneers.medica.consent.dto.ConsentDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para plantillas de consentimiento.
 *
 * Reglas:
 *  - Crear template auto-genera versión 1 en DRAFT
 *  - DRAFT es editable; PUBLISHED y ARCHIVED son inmutables
 *  - Publicar marca como current y archiva la versión anterior si existía
 *  - Para editar una versión publicada, se crea una nueva DRAFT (clonando)
 *  - Soft delete del template (active = false)
 *
 * Mejoras aplicadas:
 *  #1 Listado optimizado con batch query (sin N+1)
 *  #4 Lock pesimista en createDraftFromLatest (race condition)
 *  #6 Defensa en profundidad con tenant en repos
 *  #7 Auditoría lastEditedBy/At en cambios de DRAFT
 */
@Service
public class ConsentTemplateService {

	private final ConsentTemplateRepository templateRepo;
	private final ConsentTemplateVersionRepository versionRepo;
	private final ConsentHtmlSanitizer sanitizer;

	public ConsentTemplateService(ConsentTemplateRepository templateRepo,
			ConsentTemplateVersionRepository versionRepo,
			ConsentHtmlSanitizer sanitizer) {
		this.templateRepo = templateRepo;
		this.versionRepo = versionRepo;
		this.sanitizer = sanitizer;
	}

	// ─── CREATE TEMPLATE ──────────────────────────────────────────────

	@Transactional
	public ConsentTemplateEntity create(UUID tenantId, CreateTemplateRequest req,
			UUID actorUserId) {
		if (templateRepo.existsByTenantIdAndCode(tenantId, req.code())) {
			throw new IllegalArgumentException(
					"Ya existe una plantilla con el código: " + req.code());
		}

		ConsentTemplateEntity template = new ConsentTemplateEntity();
		template.setTenantId(tenantId);
		template.setName(req.name());
		template.setCode(req.code());
		template.setDescription(req.description());
		template.setDisplayOrder(req.displayOrder() != null ? req.displayOrder() : 0);
		template.setActive(true);
		template = templateRepo.save(template);

		ConsentTemplateVersionEntity v1 = new ConsentTemplateVersionEntity();
		v1.setTenantId(tenantId);
		v1.setTemplateId(template.getId());
		v1.setVersionNumber(1);
		v1.setTitle(req.initialTitle());
		v1.setContentHtml(sanitizer.sanitize(req.initialContentHtml()));
		v1.setStatus(ConsentVersionStatus.DRAFT);
		v1.setLastEditedAt(Instant.now());
		v1.setLastEditedByUserId(actorUserId);
		versionRepo.save(v1);

		return template;
	}

	// ─── READ ─────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<ConsentTemplateEntity> list(UUID tenantId, boolean includeInactive) {
		return includeInactive
				? templateRepo.findByTenantIdOrderByDisplayOrderAscNameAsc(tenantId)
						: templateRepo.findByTenantIdAndActiveTrueOrderByDisplayOrderAscNameAsc(tenantId);
	}

	/**
	 * Mejora #1: una sola query para obtener stats de todas las plantillas.
	 * Evita N+1 (antes: 2 queries adicionales por cada template en la lista).
	 */
	@Transactional(readOnly = true)
	public Map<UUID, TemplateVersionStats> getStatsMap(UUID tenantId) {
		return versionRepo.findStatsByTenantId(tenantId).stream()
				.collect(Collectors.toMap(
						TemplateVersionStats::templateId,
						stats -> stats));
	}

	@Transactional(readOnly = true)
	public ConsentTemplateEntity getById(UUID tenantId, UUID templateId) {
		return templateRepo.findByIdAndTenantId(templateId, tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Plantilla no encontrada"));
	}

	@Transactional(readOnly = true)
	public List<ConsentTemplateVersionEntity> listVersions(UUID tenantId, UUID templateId) {
		getById(tenantId, templateId);
		return versionRepo.findByTemplateIdAndTenantIdOrderByVersionNumberDesc(templateId, tenantId);
	}

	@Transactional(readOnly = true)
	public ConsentTemplateVersionEntity getVersion(UUID tenantId, UUID versionId) {
		return versionRepo.findByIdAndTenantId(versionId, tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Versión no encontrada"));
	}

	// ─── UPDATE TEMPLATE METADATA ─────────────────────────────────────

	@Transactional
	public ConsentTemplateEntity updateMetadata(UUID tenantId, UUID templateId,
			UpdateTemplateRequest req) {
		ConsentTemplateEntity t = getById(tenantId, templateId);
		if (req.name() != null) t.setName(req.name());
		if (req.description() != null) t.setDescription(req.description());
		if (req.displayOrder() != null) t.setDisplayOrder(req.displayOrder());
		return templateRepo.save(t);
	}

	// ─── VERSIONS ─────────────────────────────────────────────────────

	/**
	 * Crea una nueva versión DRAFT clonando la última versión existente.
	 *
	 * Mejora #4: lock pesimista sobre el template para evitar race condition
	 * cuando dos admins crean versión simultáneamente.
	 */
	@Transactional
	public ConsentTemplateVersionEntity createDraftFromLatest(UUID tenantId, UUID templateId,
			UUID actorUserId) {
		// Lock pesimista — bloquea hasta commit
		ConsentTemplateEntity t = templateRepo.findByIdAndTenantIdForUpdate(templateId, tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Plantilla no encontrada"));

		ConsentTemplateVersionEntity latest = versionRepo
				.findLatestByTemplateIdAndTenantId(templateId, tenantId)
				.orElseThrow(() -> new IllegalStateException(
						"La plantilla no tiene ninguna versión previa"));

		// Si la última ya es DRAFT, devolvemos esa en vez de crear otra
		if (latest.getStatus() == ConsentVersionStatus.DRAFT) {
			return latest;
		}

		int next = versionRepo.findMaxVersionNumber(templateId) + 1;
		ConsentTemplateVersionEntity draft = new ConsentTemplateVersionEntity();
		draft.setTenantId(tenantId);
		draft.setTemplateId(t.getId());
		draft.setVersionNumber(next);
		draft.setTitle(latest.getTitle());
		draft.setContentHtml(latest.getContentHtml());
		draft.setStatus(ConsentVersionStatus.DRAFT);
		draft.setLastEditedAt(Instant.now());
		draft.setLastEditedByUserId(actorUserId);
		return versionRepo.save(draft);
	}

	@Transactional
	public ConsentTemplateVersionEntity updateDraft(UUID tenantId, UUID versionId,
			UpdateDraftVersionRequest req,
			UUID actorUserId) {
		ConsentTemplateVersionEntity v = getVersion(tenantId, versionId);
		if (v.getStatus() != ConsentVersionStatus.DRAFT) {
			throw new IllegalStateException(
					"Solo se pueden editar versiones en estado DRAFT. " +
					"Crea una nueva versión desde la última publicada.");
		}
		if (req.title() != null) v.setTitle(req.title());
		if (req.contentHtml() != null) v.setContentHtml(sanitizer.sanitize(req.contentHtml()));
		v.setLastEditedAt(Instant.now());
		v.setLastEditedByUserId(actorUserId);
		return versionRepo.save(v);
	}

	@Transactional
	public ConsentTemplateVersionEntity publish(UUID tenantId, UUID versionId,
			UUID publishedByUserId) {
		ConsentTemplateVersionEntity v = getVersion(tenantId, versionId);
		if (v.getStatus() != ConsentVersionStatus.DRAFT) {
			throw new IllegalStateException("Solo se pueden publicar versiones DRAFT");
		}
		ConsentTemplateEntity t = getById(tenantId, v.getTemplateId());

		// Archivar la versión publicada anterior (si existía)
		if (t.getCurrentVersionId() != null) {
			ConsentTemplateVersionEntity prev = versionRepo
					.findByIdAndTenantId(t.getCurrentVersionId(), tenantId)
					.orElse(null);
			if (prev != null && prev.getStatus() == ConsentVersionStatus.PUBLISHED) {
				prev.setStatus(ConsentVersionStatus.ARCHIVED);
				versionRepo.save(prev);
			}
		}

		v.setStatus(ConsentVersionStatus.PUBLISHED);
		v.setPublishedAt(Instant.now());
		v.setPublishedByUserId(publishedByUserId);
		v = versionRepo.save(v);

		t.setCurrentVersionId(v.getId());
		templateRepo.save(t);
		return v;
	}

	@Transactional
	public void deleteDraft(UUID tenantId, UUID versionId) {
		ConsentTemplateVersionEntity v = getVersion(tenantId, versionId);
		if (v.getStatus() != ConsentVersionStatus.DRAFT) {
			throw new IllegalStateException("Solo se pueden eliminar versiones DRAFT");
		}
		if (v.getVersionNumber() == 1) {
			throw new IllegalStateException(
					"No se puede eliminar la versión 1; desactiva la plantilla en su lugar");
		}
		versionRepo.delete(v);
	}

	// ─── ACTIVATE / DEACTIVATE TEMPLATE ───────────────────────────────

	@Transactional
	public void deactivate(UUID tenantId, UUID templateId) {
		ConsentTemplateEntity t = getById(tenantId, templateId);
		t.setActive(false);
		templateRepo.save(t);
	}

	@Transactional
	public void activate(UUID tenantId, UUID templateId) {
		ConsentTemplateEntity t = getById(tenantId, templateId);
		t.setActive(true);
		templateRepo.save(t);
	}
}
