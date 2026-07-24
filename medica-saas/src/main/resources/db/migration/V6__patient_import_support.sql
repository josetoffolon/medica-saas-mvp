-- V6__patient_import_support.sql
-- Habilita migración masiva de pacientes desde agendas/sistemas previos.
-- Cambio ADITIVO: relajar NOT NULL es un widening (no rompe código existente),
-- y las columnas nuevas son nullable o tienen DEFAULT.

-- ─── 1. Relajar campos que una agenda real no tiene ───────────────────
ALTER TABLE patient
  MODIFY COLUMN email                      VARCHAR(160) NULL,
  MODIFY COLUMN document_type              VARCHAR(20)  NULL,
  MODIFY COLUMN document_number            VARCHAR(50)  NULL,
  MODIFY COLUMN gender                     VARCHAR(1)   NULL,
  MODIFY COLUMN blood_type                 VARCHAR(5)   NULL,
  MODIFY COLUMN emergency_contact_name     VARCHAR(200) NULL,
  MODIFY COLUMN emergency_contact_phone    VARCHAR(20)  NULL,
  MODIFY COLUMN emergency_contact_relation VARCHAR(50)  NULL;

-- ─── 2. '' colisiona en UNIQUE; NULL no. Normalizar vacíos históricos ──
UPDATE patient SET email           = NULL WHERE email           IS NOT NULL AND TRIM(email)           = '';
UPDATE patient SET document_number = NULL WHERE document_number IS NOT NULL AND TRIM(document_number) = '';

-- ─── 3. Columnas de trazabilidad (idempotente, patrón de V3) ──────────
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patient' AND COLUMN_NAME='data_source');
SET @sql := IF(@c=0,
  "ALTER TABLE patient
     ADD COLUMN data_source        VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
     ADD COLUMN profile_status     VARCHAR(20) NOT NULL DEFAULT 'COMPLETE',
     ADD COLUMN import_batch_id    BINARY(16)  NULL,
     ADD COLUMN legacy_external_id VARCHAR(64) NULL",
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patient' AND INDEX_NAME='idx_patient_import_batch');
SET @sql := IF(@c=0,
  'CREATE INDEX idx_patient_import_batch ON patient (tenant_id, import_batch_id)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patient' AND CONSTRAINT_NAME='uk_patient_tenant_legacy');
SET @sql := IF(@c=0,
  'ALTER TABLE patient ADD CONSTRAINT uk_patient_tenant_legacy UNIQUE (tenant_id, legacy_external_id)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;