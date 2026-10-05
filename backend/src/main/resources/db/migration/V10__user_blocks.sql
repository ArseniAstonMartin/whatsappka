-- Личные блокировки (FR-08). Блокировка односторонняя по записи, но правила взаимодействия действуют в обе стороны.

CREATE TABLE user_blocks (
    blocker_id uuid NOT NULL REFERENCES users (id),
    blocked_id uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT user_blocks_no_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX user_blocks_blocked_idx ON user_blocks (blocked_id);
CREATE INDEX user_blocks_blocker_page_idx ON user_blocks (blocker_id, created_at DESC, blocked_id DESC);
