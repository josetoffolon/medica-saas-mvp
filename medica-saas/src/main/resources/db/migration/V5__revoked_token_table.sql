-- V5: tabla de tokens revocados (#7).
-- La entidad RevokedTokenEntity existía desde el #7 pero su tabla se había
-- creado solo por ddl-auto en dev; nunca por Flyway. Producción no la tenía,
-- provocando el fallo de schema-validation. Esta migración la formaliza.

CREATE TABLE IF NOT EXISTS revoked_token (
    jti         VARCHAR(64)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    revoked_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (jti),
    KEY idx_revoked_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;