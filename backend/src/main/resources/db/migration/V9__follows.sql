-- Подписки односторонние (PRD §4, FR-08). Пара уникальна, подписка на себя запрещена на уровне схемы.

CREATE TABLE follows (
    follower_id uuid NOT NULL REFERENCES users (id),
    followee_id uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT follows_no_self CHECK (follower_id <> followee_id)
);

-- Страницы списков идут по времени подписки и id пользователя (keyset), поэтому индексы повторяют порядок.
CREATE INDEX follows_followee_page_idx ON follows (followee_id, created_at DESC, follower_id DESC);
CREATE INDEX follows_follower_page_idx ON follows (follower_id, created_at DESC, followee_id DESC);
