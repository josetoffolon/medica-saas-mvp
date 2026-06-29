-- V2: tabla hija de eventos de pago (#16, opción B)
-- Reemplaza la concatenación sin límite en payment_transaction.payload_json.

CREATE TABLE payment_event (
    id              BINARY(16)   NOT NULL,
    tenant_id       BINARY(16)   NOT NULL,
    transaction_id  BINARY(16)   NOT NULL,
    source          VARCHAR(20)  NOT NULL,
    outcome         VARCHAR(30)  NULL,
    raw_json        LONGTEXT     NULL,
    created_at      DATETIME(6)  NOT NULL,
    created_by      BINARY(16)   NULL,
    updated_at      DATETIME(6)  NULL,
    updated_by      BINARY(16)   NULL,
    PRIMARY KEY (id),
    KEY idx_pevent_tenant_tx (tenant_id, transaction_id, created_at),
    KEY idx_pevent_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;