-- Жалобы (FR-11). Ровно одна FK-цель соответствует виду объекта; у пользователя одна открытая жалоба на объект.
-- Снимок (evidence) хранит только то, что нужно для решения: для сообщения — само сообщение, не весь чат.

CREATE TABLE reports (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id uuid NOT NULL REFERENCES users (id),
    target_kind varchar(20) NOT NULL,
    target_user_id uuid REFERENCES users (id),
    target_post_id uuid REFERENCES posts (id),
    target_comment_id uuid REFERENCES comments (id),
    target_message_id uuid REFERENCES messages (id),
    target_group_id uuid REFERENCES groups (id),
    reason varchar(30) NOT NULL,
    description varchar(2000),
    status varchar(20) NOT NULL DEFAULT 'OPEN',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT reports_target_kind_allowed CHECK (target_kind IN ('USER', 'POST', 'COMMENT', 'MESSAGE', 'GROUP')),
    CONSTRAINT reports_exactly_one_target CHECK (
        num_nonnulls(target_user_id, target_post_id, target_comment_id, target_message_id, target_group_id) = 1
    ),
    CONSTRAINT reports_target_matches_kind CHECK (
        (target_kind = 'USER' AND target_user_id IS NOT NULL)
        OR (target_kind = 'POST' AND target_post_id IS NOT NULL)
        OR (target_kind = 'COMMENT' AND target_comment_id IS NOT NULL)
        OR (target_kind = 'MESSAGE' AND target_message_id IS NOT NULL)
        OR (target_kind = 'GROUP' AND target_group_id IS NOT NULL)
    ),
    CONSTRAINT reports_not_self CHECK (target_user_id IS NULL OR target_user_id <> reporter_id),
    CONSTRAINT reports_reason_allowed CHECK (reason IN (
        'SPAM', 'HARASSMENT', 'HATE', 'VIOLENCE', 'SEXUAL', 'SELF_HARM', 'IMPERSONATION', 'OTHER')),
    CONSTRAINT reports_description_length CHECK (description IS NULL OR char_length(description) <= 2000),
    CONSTRAINT reports_status_allowed CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);

-- Один открытый отчёт пользователя на объект. COALESCE нужен: в уникальном индексе NULL не совпадают.
CREATE UNIQUE INDEX reports_one_open_per_object ON reports (
    reporter_id, target_kind,
    COALESCE(target_user_id, '00000000-0000-0000-0000-000000000000'),
    COALESCE(target_post_id, '00000000-0000-0000-0000-000000000000'),
    COALESCE(target_comment_id, '00000000-0000-0000-0000-000000000000'),
    COALESCE(target_message_id, '00000000-0000-0000-0000-000000000000'),
    COALESCE(target_group_id, '00000000-0000-0000-0000-000000000000')
) WHERE status = 'OPEN';

CREATE INDEX reports_queue_idx ON reports (status, created_at, id);

CREATE TABLE report_evidence (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id uuid NOT NULL REFERENCES reports (id),
    kind varchar(20) NOT NULL,
    snapshot jsonb NOT NULL,
    captured_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT report_evidence_kind_allowed CHECK (kind = 'SNAPSHOT'),
    CONSTRAINT report_evidence_size CHECK (pg_column_size(snapshot) <= 16384)
);

CREATE INDEX report_evidence_report_idx ON report_evidence (report_id);
