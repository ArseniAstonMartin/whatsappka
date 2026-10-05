-- Уведомления о сообщениях, приглашениях и заявках (FR-09).
-- Уведомление о сообщении несёт seq сообщения: прочтение чата снимает уведомления до своего seq.
-- Результат заявки получает сам заявитель через тип JOIN_RESULT: он ещё не участник сообщества.

ALTER TABLE notifications ADD COLUMN message_seq bigint;
ALTER TABLE notifications ADD CONSTRAINT notifications_message_seq_positive CHECK (message_seq IS NULL OR message_seq > 0);

ALTER TABLE notifications DROP CONSTRAINT notifications_type_allowed;
ALTER TABLE notifications DROP CONSTRAINT notifications_target_by_type;
ALTER TABLE notification_preferences DROP CONSTRAINT notification_preferences_type_allowed;

ALTER TABLE notifications ADD CONSTRAINT notifications_type_allowed CHECK (
    type IN ('FOLLOW', 'MESSAGE', 'CHAT_INVITATION', 'COMMUNITY_INVITATION', 'JOIN_REQUEST', 'JOIN_RESULT',
             'COMMENT', 'REPLY', 'REACTION', 'SYSTEM'));

ALTER TABLE notifications ADD CONSTRAINT notifications_target_by_type CHECK (
    (type = 'SYSTEM' AND target_kind IS NULL AND target_id IS NULL AND actor_id IS NULL AND message_seq IS NULL)
    OR (type = 'FOLLOW' AND target_kind = 'USER' AND target_id = recipient_id AND actor_id IS NOT NULL AND message_seq IS NULL)
    OR (type = 'MESSAGE' AND target_kind = 'CONVERSATION' AND target_id IS NOT NULL AND actor_id IS NOT NULL
        AND message_seq IS NOT NULL)
    OR (type = 'CHAT_INVITATION' AND target_kind = 'CONVERSATION' AND target_id IS NOT NULL AND message_seq IS NULL)
    OR (type IN ('COMMUNITY_INVITATION', 'JOIN_REQUEST', 'JOIN_RESULT') AND target_kind = 'COMMUNITY'
        AND target_id IS NOT NULL AND message_seq IS NULL)
    OR (type = 'COMMENT' AND target_kind = 'POST' AND target_id IS NOT NULL AND actor_id IS NOT NULL AND message_seq IS NULL)
    OR (type = 'REPLY' AND target_kind = 'COMMENT' AND target_id IS NOT NULL AND actor_id IS NOT NULL AND message_seq IS NULL)
    OR (type = 'REACTION' AND target_kind IN ('POST', 'COMMENT') AND target_id IS NOT NULL AND actor_id IS NOT NULL
        AND message_seq IS NULL)
);

ALTER TABLE notification_preferences ADD CONSTRAINT notification_preferences_type_allowed CHECK (
    type IN ('FOLLOW', 'MESSAGE', 'CHAT_INVITATION', 'COMMUNITY_INVITATION', 'JOIN_REQUEST', 'JOIN_RESULT',
             'COMMENT', 'REPLY', 'REACTION', 'SYSTEM'));

CREATE INDEX notifications_chat_read_idx ON notifications (recipient_id, target_id, message_seq)
    WHERE type = 'MESSAGE' AND read_at IS NULL;
