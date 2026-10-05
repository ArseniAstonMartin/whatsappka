-- Вложенные комментарии (FR-05). Составная уникальность и составной FK держат ответ в пределах того
-- же поста; родителя после создания никто не меняет. Фактическая глубина не превышает 3 — ответ на
-- третий уровень присоединяется к той же тройке, адресат фиксируется в reply_to_user_id.

CREATE TABLE comments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES posts (id),
    author_id uuid NOT NULL REFERENCES users (id),
    parent_id uuid,
    reply_to_user_id uuid REFERENCES users (id),
    depth smallint NOT NULL,
    body varchar(2000),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT comments_depth_range CHECK (depth BETWEEN 1 AND 3),
    CONSTRAINT comments_root_has_no_parent CHECK ((depth = 1) = (parent_id IS NULL)),
    CONSTRAINT comments_body_length CHECK (body IS NULL OR char_length(body) <= 2000),
    CONSTRAINT comments_body_required_unless_deleted CHECK (deleted_at IS NOT NULL OR body IS NOT NULL),
    CONSTRAINT comments_same_post UNIQUE (id, post_id),
    CONSTRAINT comments_parent_same_post FOREIGN KEY (parent_id, post_id) REFERENCES comments (id, post_id)
);

-- Страница корневых комментариев поста (keyset по времени) и поиск прямых ответов под каждым.
CREATE INDEX comments_post_roots_idx ON comments (post_id, created_at, id) WHERE parent_id IS NULL;
CREATE INDEX comments_parent_idx ON comments (parent_id, created_at, id);
