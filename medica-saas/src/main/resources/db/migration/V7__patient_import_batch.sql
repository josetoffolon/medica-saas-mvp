-- V7__patient_import_batch.sql

CREATE TABLE patient_import_batch (
  id                BINARY(16)   NOT NULL,
  tenant_id         BINARY(16)   NOT NULL,
  file_name         VARCHAR(255) NOT NULL,
  file_hash         CHAR(64)     NOT NULL,
  file_size_bytes   BIGINT       NOT NULL,
  status            VARCHAR(20)  NOT NULL,
  total_rows        INT NOT NULL DEFAULT 0,
  ok_rows           INT NOT NULL DEFAULT 0,
  warning_rows      INT NOT NULL DEFAULT 0,
  error_rows        INT NOT NULL DEFAULT 0,
  duplicate_rows    INT NOT NULL DEFAULT 0,
  imported_rows     INT NOT NULL DEFAULT 0,
  skipped_rows      INT NOT NULL DEFAULT 0,
  error_message     VARCHAR(500) NULL,
  committed_at      DATETIME(6)  NULL,
  reverted_at       DATETIME(6)  NULL,
  created_at        DATETIME(6)  NOT NULL,
  updated_at        DATETIME(6)  NOT NULL,
  created_by        BINARY(16)   NULL,
  updated_by        BINARY(16)   NULL,
  PRIMARY KEY (id),
  KEY idx_import_batch_tenant (tenant_id, created_at),
  CONSTRAINT uk_import_batch_tenant_hash UNIQUE (tenant_id, file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE patient_import_row (
  id              BINARY(16)  NOT NULL,
  tenant_id       BINARY(16)  NOT NULL,
  batch_id        BINARY(16)  NOT NULL,
  `row_number`    INT         NOT NULL,
  raw_data        TEXT        NOT NULL,
  normalized_data TEXT        NULL,
  status          VARCHAR(20) NOT NULL,
  messages        TEXT        NULL,
  match_patient_id BINARY(16) NULL,
  match_reason    VARCHAR(40) NULL,
  patient_id      BINARY(16)  NULL,
  created_at      DATETIME(6) NOT NULL,
  updated_at      DATETIME(6) NOT NULL,
  created_by      BINARY(16)  NULL,
  updated_by      BINARY(16)  NULL,
  PRIMARY KEY (id),
  KEY idx_import_row_batch (batch_id, status),
  CONSTRAINT fk_import_row_batch FOREIGN KEY (batch_id)
    REFERENCES patient_import_batch (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;