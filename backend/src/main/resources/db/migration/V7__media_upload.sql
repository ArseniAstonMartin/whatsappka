-- Загрузка медиа: назначение файла и резервирование квоты на время передачи (FR-10).

ALTER TABLE media_assets
    ADD COLUMN purpose varchar(20) NOT NULL,
    ADD CONSTRAINT media_assets_purpose_allowed CHECK (purpose IN ('AVATAR', 'COVER', 'POST_IMAGE', 'CHAT_IMAGE', 'CHAT_DOCUMENT'));

-- Пока загрузка идёт, байты уже учитываются в квоте. Строка удаляется в той же транзакции, что и создание медиа.
CREATE TABLE media_reservations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id uuid NOT NULL REFERENCES users (id),
    size_bytes bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    CONSTRAINT media_reservations_size_positive CHECK (size_bytes > 0),
    CONSTRAINT media_reservations_expiry_after_creation CHECK (expires_at > created_at)
);

CREATE INDEX media_reservations_owner_expiry_idx ON media_reservations (owner_id, expires_at);
CREATE INDEX media_reservations_expiry_idx ON media_reservations (expires_at);
