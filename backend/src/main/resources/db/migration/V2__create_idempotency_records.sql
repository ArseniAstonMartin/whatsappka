-- Внешний ключ user_id → users добавляется миграцией identity.
CREATE TABLE idempotency_records (
    user_id uuid NOT NULL,
    operation varchar(100) NOT NULL,
    key varchar(255) NOT NULL,
    request_hash varchar(64) NOT NULL,
    response_status integer NOT NULL,
    response_body jsonb NOT NULL,
    expires_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, operation, key),
    CONSTRAINT idempotency_records_operation_length CHECK (char_length(operation) BETWEEN 1 AND 100),
    CONSTRAINT idempotency_records_key_length CHECK (char_length(key) BETWEEN 1 AND 255),
    CONSTRAINT idempotency_records_request_hash_hex CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT idempotency_records_response_status_range CHECK (response_status BETWEEN 100 AND 599)
);
