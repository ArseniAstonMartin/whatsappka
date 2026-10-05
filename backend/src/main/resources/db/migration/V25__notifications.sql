-- Уведомления (FR-09). Уведомление хранит только тип, ссылку на объект и актора.
-- Текст сообщения в уведомление не попадает: подробности читаются по текущему доступу.

CREATE TABLE notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id uuid NOT NULL REFERENCES users (id),
    event_key varchar(120) NOT NULL,
    type varchar(30) NOT NULL,
    actor_id uuid REFERENCES users (id),
    target_kind varchar(20),
    target_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    read_at timestamptz,
    CONSTRAINT notifications_type_allowed CHECK (
        type IN ('FOLLOW', 'MESSAGE', 'CHAT_INVITATION', 'COMMUNITY_INVITATION', 'JOIN_REQUEST', 'SYSTEM')),
    CONSTRAINT notifications_event_key_length CHECK (char_length(event_key) BETWEEN 1 AND 120),
    CONSTRAINT notifications_not_self CHECK (actor_id IS NULL OR actor_id <> recipient_id),
    -- Ссылка соответствует типу: системное без цели, подписка — на самого получателя, остальные — на свой вид объекта.
    CONSTRAINT notifications_target_by_type CHECK (
        (type = 'SYSTEM' AND target_kind IS NULL AND target_id IS NULL AND actor_id IS NULL)
        OR (type = 'FOLLOW' AND target_kind = 'USER' AND target_id = recipient_id AND actor_id IS NOT NULL)
        OR (type = 'MESSAGE' AND target_kind = 'CONVERSATION' AND target_id IS NOT NULL)
        OR (type = 'CHAT_INVITATION' AND target_kind = 'CONVERSATION' AND target_id IS NOT NULL)
        OR (type IN ('COMMUNITY_INVITATION', 'JOIN_REQUEST') AND target_kind = 'COMMUNITY' AND target_id IS NOT NULL)
    ),
    CONSTRAINT notifications_event_unique UNIQUE (event_key, recipient_id, type)
);

CREATE INDEX notifications_recipient_idx ON notifications (recipient_id, created_at DESC, id DESC);
CREATE INDEX notifications_unread_idx ON notifications (recipient_id) WHERE read_at IS NULL;

CREATE TABLE notification_preferences (
    user_id uuid NOT NULL REFERENCES users (id),
    type varchar(30) NOT NULL,
    enabled boolean NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, type),
    CONSTRAINT notification_preferences_type_allowed CHECK (
        type IN ('FOLLOW', 'MESSAGE', 'CHAT_INVITATION', 'COMMUNITY_INVITATION', 'JOIN_REQUEST', 'SYSTEM')),
    -- Системные уведомления отключить нельзя.
    CONSTRAINT notification_preferences_system_on CHECK (type <> 'SYSTEM' OR enabled)
);
