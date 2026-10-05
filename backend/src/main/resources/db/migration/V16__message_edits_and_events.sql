-- Правки, удаление и журнал сообщений (FR-06, PRD §5.3).
-- Удалённое сообщение не хранит текста. Модераторское удаление пишет причину в аудит.

ALTER TABLE messages ADD CONSTRAINT messages_deleted_has_no_body CHECK (deleted_at IS NULL OR body IS NULL);

ALTER TABLE conversation_events ADD COLUMN message_id uuid REFERENCES messages (id);
ALTER TABLE conversation_events ADD CONSTRAINT conversation_events_message_required
    CHECK (type NOT LIKE 'message.%' OR message_id IS NOT NULL);

-- События с номером меньше floor удалены: клиент с таким курсором должен пройти полную синхронизацию.
ALTER TABLE conversations ADD COLUMN events_floor bigint NOT NULL DEFAULT 0;
ALTER TABLE conversations ADD CONSTRAINT conversations_events_floor_non_negative CHECK (events_floor >= 0);

CREATE TABLE message_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id uuid NOT NULL REFERENCES messages (id),
    conversation_id uuid NOT NULL REFERENCES conversations (id),
    actor_id uuid NOT NULL REFERENCES users (id),
    action varchar(30) NOT NULL,
    reason varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT message_audit_action_allowed CHECK (action IN ('DELETED_BY_ADMIN')),
    CONSTRAINT message_audit_reason_length CHECK (char_length(reason) BETWEEN 1 AND 200)
);

CREATE INDEX message_audit_message_idx ON message_audit (message_id);
