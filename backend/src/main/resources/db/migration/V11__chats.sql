-- Чаты (FR-06). Личный диалог уникален для пары; пару храним упорядоченно (user_low_id < user_high_id).
-- Участие в чате — интервал членства: повторное вступление создаёт новую строку с новым id.

CREATE TABLE conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    type varchar(10) NOT NULL,
    title varchar(100),
    owner_id uuid REFERENCES users (id),
    avatar_media_id uuid REFERENCES media_assets (id),
    next_seq bigint NOT NULL DEFAULT 0,
    next_event_seq bigint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT conversations_type_allowed CHECK (type IN ('DIRECT', 'GROUP')),
    CONSTRAINT conversations_title_length CHECK (title IS NULL OR char_length(title) BETWEEN 1 AND 100),
    CONSTRAINT conversations_counters_non_negative CHECK (next_seq >= 0 AND next_event_seq >= 0)
);

CREATE INDEX conversations_updated_idx ON conversations (updated_at DESC, id DESC);

CREATE TABLE direct_conversations (
    conversation_id uuid PRIMARY KEY REFERENCES conversations (id),
    user_low_id uuid NOT NULL REFERENCES users (id),
    user_high_id uuid NOT NULL REFERENCES users (id),
    CONSTRAINT direct_conversations_pair_unique UNIQUE (user_low_id, user_high_id),
    CONSTRAINT direct_conversations_ordered CHECK (user_low_id < user_high_id)
);

CREATE TABLE conversation_memberships (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES conversations (id),
    user_id uuid NOT NULL REFERENCES users (id),
    role varchar(10) NOT NULL,
    joined_seq bigint NOT NULL,
    left_seq bigint,
    joined_at timestamptz NOT NULL DEFAULT now(),
    left_at timestamptz,
    last_read_seq bigint NOT NULL DEFAULT 0,
    CONSTRAINT conversation_memberships_role_allowed CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT conversation_memberships_left_pair CHECK ((left_at IS NULL) = (left_seq IS NULL))
);

-- Один действующий интервал на пару «чат, пользователь».
CREATE UNIQUE INDEX conversation_memberships_active_key
    ON conversation_memberships (conversation_id, user_id) WHERE left_at IS NULL;
CREATE INDEX conversation_memberships_user_idx ON conversation_memberships (user_id) WHERE left_at IS NULL;
