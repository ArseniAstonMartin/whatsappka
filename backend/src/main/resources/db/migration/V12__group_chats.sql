-- Групповые чаты (FR-06). Владелец хранится у чата (owner_id), а не ролью в строке членства (PRD §6).
-- Все изменения состава пишутся в conversation_events с собственной последовательностью.

ALTER TABLE conversations
    ADD CONSTRAINT conversations_group_fields CHECK (
        (type = 'GROUP' AND owner_id IS NOT NULL AND title IS NOT NULL)
        OR (type = 'DIRECT' AND owner_id IS NULL AND title IS NULL)
    );

CREATE TABLE conversation_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES conversations (id),
    event_seq bigint NOT NULL,
    type varchar(40) NOT NULL,
    actor_id uuid REFERENCES users (id),
    subject_user_id uuid REFERENCES users (id),
    occurred_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT conversation_events_seq_positive CHECK (event_seq > 0),
    CONSTRAINT conversation_events_seq_unique UNIQUE (conversation_id, event_seq)
);

-- Аватар группы доступен её участникам: это новый тип связи медиа.
ALTER TABLE media_links DROP CONSTRAINT media_links_type_allowed;
ALTER TABLE media_links ADD CONSTRAINT media_links_type_allowed
    CHECK (link_type IN ('PROFILE_AVATAR', 'PROFILE_COVER', 'GROUP_AVATAR'));
