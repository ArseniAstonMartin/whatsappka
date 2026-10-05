-- Санкции аккаунтов (FR-11, NFR-SEC-01/02).
-- SUSPENDED — аккаунт ограничен: вход, REST и WebSocket закрыты, а автор и его публикации исчезают из выдачи
-- тем же фильтром status = 'ACTIVE', который уже стоит во всех запросах. Снятие и истечение возвращают ACTIVE,
-- но не возвращают отдельно скрытые или удалённые материалы: санкция их не трогает.

ALTER TABLE users DROP CONSTRAINT users_status_allowed;
ALTER TABLE users ADD CONSTRAINT users_status_allowed CHECK (status IN ('ACTIVE', 'DISABLED', 'SUSPENDED'));

CREATE TABLE user_sanctions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id),
    kind varchar(20) NOT NULL,
    reason varchar(2000) NOT NULL,
    issued_by uuid NOT NULL REFERENCES users (id),
    issued_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz,
    lifted_at timestamptz,
    lifted_by uuid REFERENCES users (id),
    lift_reason varchar(2000),
    CONSTRAINT user_sanctions_kind_allowed CHECK (kind IN ('TEMPORARY', 'PERMANENT')),
    CONSTRAINT user_sanctions_reason_length CHECK (char_length(reason) BETWEEN 1 AND 2000),
    CONSTRAINT user_sanctions_expiry_matches_kind CHECK ((kind = 'PERMANENT') = (expires_at IS NULL)),
    CONSTRAINT user_sanctions_expiry_after_issue CHECK (expires_at IS NULL OR expires_at > issued_at),
    CONSTRAINT user_sanctions_not_self CHECK (issued_by <> user_id),
    -- Снятие: либо модератор/администратор (lifted_by), либо истечение срока (lifted_by пуст, причина системная).
    CONSTRAINT user_sanctions_lift_consistent CHECK ((lifted_at IS NULL) = (lift_reason IS NULL)),
    CONSTRAINT user_sanctions_lifted_by_needs_time CHECK (lifted_by IS NULL OR lifted_at IS NOT NULL)
);

CREATE INDEX user_sanctions_user_active_idx ON user_sanctions (user_id) WHERE lifted_at IS NULL;
CREATE INDEX user_sanctions_expiry_idx ON user_sanctions (expires_at) WHERE lifted_at IS NULL AND expires_at IS NOT NULL;
