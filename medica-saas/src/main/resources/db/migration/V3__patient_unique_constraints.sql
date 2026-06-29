-- V3__patient_unique_constraints.sql
-- #11: unicidad de email y documento por tenant.
-- Idempotente: en tu BD de dev los constraints ya existen (se aplicaron a mano);
-- en una BD nueva se crean aquí. Por eso comprobamos information_schema antes.

-- email
SET @exists_email := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'patient'
    AND CONSTRAINT_NAME = 'uk_patient_tenant_email'
);
SET @drop_idx_email := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'patient'
    AND INDEX_NAME = 'idx_patient_tenant_email'
);

SET @sql := IF(@drop_idx_email > 0,
  'ALTER TABLE patient DROP INDEX idx_patient_tenant_email',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@exists_email = 0,
  'ALTER TABLE patient ADD CONSTRAINT uk_patient_tenant_email UNIQUE (tenant_id, email)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- documento
SET @exists_doc := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'patient'
    AND CONSTRAINT_NAME = 'uk_patient_tenant_document'
);
SET @drop_idx_doc := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'patient'
    AND INDEX_NAME = 'idx_patient_tenant_document'
);

SET @sql := IF(@drop_idx_doc > 0,
  'ALTER TABLE patient DROP INDEX idx_patient_tenant_document',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@exists_doc = 0,
  'ALTER TABLE patient ADD CONSTRAINT uk_patient_tenant_document UNIQUE (tenant_id, document_number)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;