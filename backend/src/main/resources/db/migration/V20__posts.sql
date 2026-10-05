-- Публикации (FR-03, FR-07). Эта миграция — CRUD черновиков (TASK-041); публикация, расписание,
-- хештеги, лента, комментарии и реакции того же эпика добавляют поведение поверх тех же таблиц.

CREATE TABLE posts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id uuid NOT NULL REFERENCES users (id),
    group_id uuid REFERENCES groups (id),
    body text,
    status varchar(10) NOT NULL DEFAULT 'DRAFT',
    published_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT posts_status_allowed CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED')),
    CONSTRAINT posts_body_length CHECK (body IS NULL OR char_length(body) <= 10000)
);

CREATE TABLE post_media (
    post_id uuid NOT NULL REFERENCES posts (id),
    media_id uuid NOT NULL REFERENCES media_assets (id),
    position smallint NOT NULL,
    PRIMARY KEY (post_id, media_id),
    CONSTRAINT post_media_position_range CHECK (position BETWEEN 1 AND 10),
    CONSTRAINT post_media_position_unique UNIQUE (post_id, position)
);

-- Вложение принадлежит одной публикации: повторно прикрепить тот же файл нельзя.
CREATE UNIQUE INDEX post_media_media_unique ON post_media (media_id);

-- Список своих черновиков и отложенных записей, от недавно изменённых.
CREATE INDEX posts_author_idx ON posts (author_id, updated_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX posts_group_idx ON posts (group_id, published_at DESC)
    WHERE deleted_at IS NULL AND status = 'PUBLISHED';
