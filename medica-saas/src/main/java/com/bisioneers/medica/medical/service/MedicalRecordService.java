package com.bisioneers.medica.medical.service;

import com.bisioneers.medica.medical.domain.MedicalRecordEntity;
import com.bisioneers.medica.medical.domain.MedicalRecordRepository;
import com.bisioneers.medica.medical.dto.MedicalDtos.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**

- Servicio de negocio para historial clínico (Medical Records).
- 
- Reglas de negocio:
- - Un registro firmado (signed=true) no puede editarse
- - El patientId no puede cambiarse después de la creación
- - Solo el médico que firmó debería poder des-firmar (fase 2)
    */
    @Service
    public class MedicalRecordService {
  
  private final MedicalRecordRepository recordRepository;
  
  public MedicalRecordService(MedicalRecordRepository recordRepository) {
  this.recordRepository = recordRepository;
  }
  
  // ─── CREATE ───────────────────────────────────────────────────────
  
  @Transactional
  public MedicalRecordEntity create(UUID tenantId, CreateRecordRequest request) {
  MedicalRecordEntity entity = new MedicalRecordEntity();
  entity.setTenantId(tenantId);
  entity.setPatientId(request.patientId());
  entity.setAppointmentId(request.appointmentId());
  entity.setRecordDate(request.recordDate());
  entity.setRecordType(request.recordType());
  entity.setTitle(request.title());
  entity.setContent(request.content());
  entity.setDiagnosis(request.diagnosis());
  entity.setTreatment(request.treatment());
  entity.setInstructions(request.instructions());
  entity.setPatientVisible(
  request.patientVisible() != null ? request.patientVisible() : false);
  
   return recordRepository.save(entity);
  
  }
  
  // ─── READ ─────────────────────────────────────────────────────────
  
  @Transactional(readOnly = true)
  public MedicalRecordEntity getById(UUID tenantId, UUID recordId) {
  MedicalRecordEntity record = recordRepository.findById(recordId)
  .orElseThrow(() -> new IllegalArgumentException(("Registro médico no encontrado")));
  
   if (!record.getTenantId().equals(tenantId)) {
       throw new IllegalArgumentException("Acceso denegado");
   }
  
   return record;
  
  }
  
  /**
  - Historial clínico de un paciente (paginado).
    */
    @Transactional(readOnly = true)
    public Page<MedicalRecordEntity> getByPatient(UUID tenantId, UUID patientId, Pageable pageable) {
    return recordRepository.findByTenantIdAndPatientIdOrderByRecordDateDesc(
    tenantId, patientId, pageable);
    }
  
  /**
  - Registros asociados a una cita específica.
    */
    @Transactional(readOnly = true)
    public List<MedicalRecordEntity> getByAppointment(UUID tenantId, UUID appointmentId) {
    return recordRepository.findByTenantIdAndAppointmentId(tenantId, appointmentId);
    }
  
  // ─── UPDATE ───────────────────────────────────────────────────────
  
  @Transactional
  public MedicalRecordEntity update(UUID tenantId, UUID recordId, UpdateRecordRequest request) {
  MedicalRecordEntity existing = getById(tenantId, recordId);
  
   if (existing.isSigned()) {
       throw new IllegalStateException(
               "No se puede editar un registro médico que ya ha sido firmado");
   }
  
   if (request.recordType() != null) existing.setRecordType(request.recordType());
   if (request.title() != null) existing.setTitle(request.title());
   if (request.content() != null) existing.setContent(request.content());
   if (request.diagnosis() != null) existing.setDiagnosis(request.diagnosis());
   if (request.treatment() != null) existing.setTreatment(request.treatment());
   if (request.instructions() != null) existing.setInstructions(request.instructions());
   if (request.patientVisible() != null) existing.setPatientVisible(request.patientVisible());
  
   return recordRepository.save(existing);
   
  }
  
  // ─── SIGN / UNSIGN ───────────────────────────────────────────────
  
  /**
  - Firmar un registro médico. Una vez firmado no se puede editar.
    */
    @Transactional
    public MedicalRecordEntity sign(UUID tenantId, UUID recordId) {
    MedicalRecordEntity record = getById(tenantId, recordId);
    
    if (record.isSigned()) {
    throw new IllegalStateException("El registro ya está firmado");
    }
    
    record.setSigned(true);
    return recordRepository.save(record);
    }
  
  /**
  - Des-firmar un registro para permitir correcciones.
  - En fase 2: validar que solo el médico firmante pueda hacerlo.
    */
    @Transactional
    public MedicalRecordEntity unsign(UUID tenantId, UUID recordId) {
    MedicalRecordEntity record = getById(tenantId, recordId);
    
    if (!record.isSigned()) {
    throw new IllegalStateException("El registro no está firmado");
    }
    
    record.setSigned(false);
    return recordRepository.save(record);
    }
    }
