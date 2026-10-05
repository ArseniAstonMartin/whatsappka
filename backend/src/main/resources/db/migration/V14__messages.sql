-- Сообщения (FR-06). seq — возрастающий номер внутри чата; client_message_id делает повтор безопасным.
-- Отпечаток запроса хранится, чтобы повтор с другим содержимым отличался от возврата сохранённого.

CREATE TABLE messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES conversations (id),
    sender_id uuid NOT NULL REFERENCES users (id),
    seq bigint NOT NULL,
    client_message_id uuid NOT NULL,
    body text,
    fingerprint varchar(64) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT messages_seq_positive CHECK (seq > 0),
    CONSTRAINT messages_body_length CHECK (body IS NULL OR char_length(body) <= 4000),
    CONSTRAINT messages_fingerprint_hex CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT messages_conversation_seq_unique UNIQUE (conversation_id, seq),
    CONSTRAINT messages_client_id_unique UNIQUE (conversation_id, sender_id, client_message_id)
);

CREATE TABLE message_media (
    message_id uuid NOT NULL REFERENCES messages (id),
    media_id uuid NOT NULL REFERENCES media_assets (id),
    position smallint NOT NULL,
    PRIMARY KEY (message_id, media_id),
    CONSTRAINT message_media_position_range CHECK (position BETWEEN 1 AND 5),
    CONSTRAINT message_media_position_unique UNIQUE (message_id, position)
);

-- Вложение принадлежит одному сообщению: повторно прикрепить тот же файл нельзя.
CREATE UNIQUE INDEX message_media_media_unique ON message_media (media_id);

CREATE INDEX messages_conversation_seq_idx ON messages (conversation_id, seq DESC);
