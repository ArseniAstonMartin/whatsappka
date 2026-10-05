-- Очередь жалоб и решения модератора (FR-11, FR-09).
-- Переходы: OPEN → IN_REVIEW (взял в работу) → RESOLVED или REJECTED. Каждый переход пишется в moderation_actions.

-- Статусы жалоб: DISMISSED переименован в REJECTED (записей с ним в проде ещё не было).
ALTER TABLE reports DROP CONSTRAINT reports_status_allowed;
UPDATE reports SET status = 'REJECTED' WHERE status = 'DISMISSED';
ALTER TABLE reports ADD CONSTRAINT reports_status_allowed CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED'));

ALTER TABLE reports ADD COLUMN assignee_id uuid REFERENCES users (id);
ALTER TABLE reports ADD COLUMN decided_at timestamptz;
ALTER TABLE reports ADD CONSTRAINT reports_assignee_matches_status CHECK ((status = 'OPEN') = (assignee_id IS NULL));
ALTER TABLE reports ADD CONSTRAINT reports_decided_matches_status CHECK ((status IN ('RESOLVED', 'REJECTED')) = (decided_at IS NOT NULL));

-- Открытой считается жалоба, которую ещё не решили: на неё нельзя подать вторую от того же пользователя.
DROP INDEX reports_one_open_per_object;
CREATE UNIQUE INDEX reports_one_open_per_object ON reports (
    reporter_id, target_kind,
    COALESCE(target_user_id, '00000000-0000-0000-0000-000000000000'),
    COALESCE(target_post_id, '00000000-0000-0000-0000-000000000000'),
    COALESCE(target_comment_id, '00000000-0000-0000-0000-000000000000'),
    COALESCE(target_message_id, '00000000-0000-0000-0000-000000000000'),
    COALESCE(target_group_id, '00000000-0000-0000-0000-000000000000')
) WHERE status IN ('OPEN', 'IN_REVIEW');

-- Скрытие поста: статус HIDDEN. Все запросы по опубликованным постам его исключают автоматически.
ALTER TABLE posts DROP CONSTRAINT posts_status_allowed;
ALTER TABLE posts ADD CONSTRAINT posts_status_allowed CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'HIDDEN'));

-- Скрытие комментария: отметка времени; читающие запросы комментариев её исключают.
ALTER TABLE comments ADD COLUMN hidden_at timestamptz;

CREATE TABLE moderation_actions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id uuid NOT NULL REFERENCES reports (id),
    actor_id uuid NOT NULL REFERENCES users (id),
    action varchar(30) NOT NULL,
    from_status varchar(20),
    to_status varchar(20) NOT NULL,
    reason varchar(2000),
    created_at timestamptz NOT NULL DEFAULT now(),
    trace_id varchar(64) NOT NULL,
    CONSTRAINT moderation_actions_action_allowed CHECK (action IN ('TAKE', 'REJECT', 'RESOLVE_HIDE', 'RESOLVE_NO_ACTION', 'RESTORE')),
    CONSTRAINT moderation_actions_reason_required CHECK (
        action = 'TAKE' OR (reason IS NOT NULL AND char_length(reason) BETWEEN 1 AND 2000))
);
CREATE INDEX moderation_actions_report_idx ON moderation_actions (report_id, created_at);

-- Уведомления о решении: автору скрытого материала и заявителю. Они ссылаются на жалобу (FK, отложенный в TASK-066).
ALTER TABLE notifications ADD COLUMN report_id uuid REFERENCES reports (id);
ALTER TABLE notifications DROP CONSTRAINT notifications_type_allowed;
ALTER TABLE notifications DROP CONSTRAINT notifications_target_by_type;
ALTER TABLE notification_preferences DROP CONSTRAINT notification_preferences_type_allowed;

ALTER TABLE notifications ADD CONSTRAINT notifications_type_allowed CHECK (
    type IN ('FOLLOW', 'MESSAGE', 'CHAT_INVITATION', 'COMMUNITY_INVITATION', 'JOIN_REQUEST', 'JOIN_RESULT',
             'COMMENT', 'REPLY', 'REACTION', 'SYSTEM', 'MODERATION_RESULT', 'CONTENT_HIDDEN'));

ALTER TABLE notifications ADD CONSTRAINT notifications_target_by_type CHECK (
    (type = 'SYSTEM' AND target_kind IS NULL AND target_id IS NULL AND actor_id IS NULL AND message_seq IS NULL)
    OR (type IN ('MODERATION_RESULT', 'CONTENT_HIDDEN') AND target_kind IS NULL AND target_id IS NULL
        AND actor_id IS NULL AND message_seq IS NULL)
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

-- Ссылка на жалобу есть именно у уведомлений о решении.
ALTER TABLE notifications ADD CONSTRAINT notifications_report_by_type CHECK (
    (type IN ('MODERATION_RESULT', 'CONTENT_HIDDEN')) = (report_id IS NOT NULL));

ALTER TABLE notification_preferences ADD CONSTRAINT notification_preferences_type_allowed CHECK (
    type IN ('FOLLOW', 'MESSAGE', 'CHAT_INVITATION', 'COMMUNITY_INVITATION', 'JOIN_REQUEST', 'JOIN_RESULT',
             'COMMENT', 'REPLY', 'REACTION', 'SYSTEM', 'MODERATION_RESULT', 'CONTENT_HIDDEN'));

-- Уведомления о решении нельзя отключить, как и системные: это сведения о судьбе материала или жалобы.
ALTER TABLE notification_preferences DROP CONSTRAINT notification_preferences_system_on;
ALTER TABLE notification_preferences ADD CONSTRAINT notification_preferences_mandatory_on CHECK (
    type NOT IN ('SYSTEM', 'MODERATION_RESULT', 'CONTENT_HIDDEN') OR enabled);
