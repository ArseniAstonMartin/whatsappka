-- Сообщества (FR-07, PRD §3 таблицы 11-12). OWNER хранится в groups.owner_id, а не ролью в составе
-- (так же, как у группового чата в V12): строка владельца в group_members существует, но его роль
-- не является источником полномочий OWNER.

CREATE TABLE groups (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug varchar(60) NOT NULL,
    name varchar(100) NOT NULL,
    description varchar(2000) NOT NULL DEFAULT '',
    visibility varchar(10) NOT NULL,
    owner_id uuid NOT NULL REFERENCES users (id),
    avatar_media_id uuid,
    cover_media_id uuid,
    hidden_at timestamptz,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT groups_slug_length CHECK (char_length(slug) BETWEEN 3 AND 60),
    CONSTRAINT groups_slug_format CHECK (slug ~ '^[a-z0-9][a-z0-9-]*[a-z0-9]$'),
    CONSTRAINT groups_name_length CHECK (char_length(name) BETWEEN 1 AND 100),
    CONSTRAINT groups_description_length CHECK (char_length(description) <= 2000),
    CONSTRAINT groups_visibility_allowed CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
);

CREATE UNIQUE INDEX groups_slug_lower_key ON groups (lower(slug));

-- Каталог читает только публичные, не скрытые и не удалённые записи постранично по времени создания.
CREATE INDEX groups_catalog_idx ON groups (created_at DESC, id DESC)
    WHERE visibility = 'PUBLIC' AND hidden_at IS NULL AND deleted_at IS NULL;

CREATE TABLE group_members (
    group_id uuid NOT NULL REFERENCES groups (id),
    user_id uuid NOT NULL REFERENCES users (id),
    role varchar(10) NOT NULL,
    joined_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT group_members_role_allowed CHECK (role IN ('ADMIN', 'MEMBER'))
);

CREATE INDEX group_members_user_idx ON group_members (user_id);
