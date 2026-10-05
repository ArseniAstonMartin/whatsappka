-- Уведомления о обсуждениях (FR-09): комментарии, ответы и реакции.
-- Реакция может ссылаться на пост или комментарий, поэтому вид цели проверяется вместе с типом.

ALTER TABLE notifications DROP CONSTRAINT notifications_type_allowed;
ALTER TABLE notifications DROP CONSTRAINT notifications_target_by_type;
ALTER TABLE notification_preferences DROP CONSTRAINT notification_preferences_type_allowed;

ALTER TABLE notifications ADD CONSTRAINT notifications_type_allowed CHECK (
    type IN ('FOLLOW', 'MESSAGE', 'CHAT_INVITATION', 'COMMUNITY_INVITATION', 'JOIN_REQUEST',
             'COMMENT', 'REPLY', 'REACTION', 'SYSTEM'));

ALTER TABLE notifications ADD CONSTRAINT notifications_target_by_type CHECK (
    (type = 'SYSTEM' AND target_kind IS NULL AND target_id IS NULL AND actor_id IS NULL)
    OR (type = 'FOLLOW' AND target_kind = 'USER' AND target_id = recipient_id AND actor_id IS NOT NULL)
    OR (type = 'MESSAGE' AND target_kind = 'CONVERSATION' AND target_id IS NOT NULL)
    OR (type = 'CHAT_INVITATION' AND target_kind = 'CONVERSATION' AND target_id IS NOT NULL)
    OR (type IN ('COMMUNITY_INVITATION', 'JOIN_REQUEST') AND target_kind = 'COMMUNITY' AND target_id IS NOT NULL)
    OR (type = 'COMMENT' AND target_kind = 'POST' AND target_id IS NOT NULL AND actor_id IS NOT NULL)
    OR (type = 'REPLY' AND target_kind = 'COMMENT' AND target_id IS NOT NULL AND actor_id IS NOT NULL)
    OR (type = 'REACTION' AND target_kind IN ('POST', 'COMMENT') AND target_id IS NOT NULL AND actor_id IS NOT NULL)
);

ALTER TABLE notification_preferences ADD CONSTRAINT notification_preferences_type_allowed CHECK (
    type IN ('FOLLOW', 'MESSAGE', 'CHAT_INVITATION', 'COMMUNITY_INVITATION', 'JOIN_REQUEST',
             'COMMENT', 'REPLY', 'REACTION', 'SYSTEM'));
