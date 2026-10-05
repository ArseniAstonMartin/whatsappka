-- Хештеги публикаций (FR-03, FR-12). normalized_name уникально: регистр и форма Unicode значения не имеют.

CREATE TABLE hashtags (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_name varchar(64) NOT NULL,
    display_name varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT hashtags_normalized_name_length CHECK (char_length(normalized_name) BETWEEN 1 AND 64)
);

CREATE UNIQUE INDEX hashtags_normalized_name_key ON hashtags (normalized_name);

CREATE TABLE post_hashtags (
    post_id uuid NOT NULL REFERENCES posts (id),
    hashtag_id uuid NOT NULL REFERENCES hashtags (id),
    PRIMARY KEY (post_id, hashtag_id)
);

-- Список постов по тегу (от новых опубликованных) без обхода hashtags.id.
CREATE INDEX post_hashtags_hashtag_idx ON post_hashtags (hashtag_id, post_id);
