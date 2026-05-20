package com.bisioneers.medica.documents.service;

import com.bisioneers.medica.documents.domain.DocumentTemplateEntity;
import com.bisioneers.medica.documents.domain.DocumentTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Servicio de gestión de plantillas de documentos.
 *
 * Reglas:
 *  - Las plantillas con isSystem=true no se pueden editar ni eliminar
 *  - Editar una plantilla incrementa la versión
 *  - Eliminar es soft-delete (active=false)
 *  - Documentos ya generados con esta plantilla NO se ven afectados
 *    (cada documento guarda snapshot del HTML al momento de generarse)
 */
@Service
public class DocumentTemplateService {

	private final DocumentTemplateRepository templateRepository;

	public DocumentTemplateService(DocumentTemplateRepository templateRepository) {
		this.templateRepository = templateRepository;
	}

	@Transactional(readOnly = true)
	public List<DocumentTemplateEntity> listActive(UUID tenantId) {
		return templateRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
	}

	@Transactional(readOnly = true)
	public List<DocumentTemplateEntity> listByType(UUID tenantId, String documentType) {
		return templateRepository.findByTenantIdAndDocumentTypeAndActiveTrueOrderByNameAsc(
				tenantId, documentType);
	}

	@Transactional(readOnly = true)
	public DocumentTemplateEntity getById(UUID tenantId, UUID id) {
		return templateRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Plantilla no encontrada"));
	}

	@Transactional
	public DocumentTemplateEntity create(UUID tenantId, String name, String documentType,
			String contentHtml, String description) {
		DocumentTemplateEntity entity = new DocumentTemplateEntity();
		entity.setTenantId(tenantId);
		entity.setName(name);
		entity.setDocumentType(documentType);
		entity.setContentHtml(contentHtml);
		entity.setDescription(description);
		entity.setVersion(1);
		entity.setActive(true);
		entity.setSystem(false);
		return templateRepository.save(entity);
	}

	@Transactional
	public DocumentTemplateEntity update(UUID tenantId, UUID id, String name,
			String contentHtml, String description) {
		DocumentTemplateEntity existing = getById(tenantId, id);

		if (existing.isSystem()) {
			throw new IllegalStateException(
					"No se pueden editar las plantillas del sistema. Crea una plantilla personalizada.");
		}

		existing.setName(name);
		existing.setContentHtml(contentHtml);
		existing.setDescription(description);
		existing.setVersion(existing.getVersion() + 1);
		return templateRepository.save(existing);
	}

	@Transactional
	public void deactivate(UUID tenantId, UUID id) {
		DocumentTemplateEntity existing = getById(tenantId, id);

		if (existing.isSystem()) {
			throw new IllegalStateException("No se pueden eliminar las plantillas del sistema.");
		}

		existing.setActive(false);
		templateRepository.save(existing);
	}
}
