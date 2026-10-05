-- Реакции на посты и комментарии (FR-05). Одна активная реакция пользователя на объект — составной
-- PK вместо отдельного uniq-индекса; повтор того же типа и смена типа оба идут через один upsert.

CREATE TABLE post_reactions (
    post_id uuid NOT NULL REFERENCES posts (id),
    user_id uuid NOT NULL REFERENCES users (id),
    type varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT post_reactions_type_known CHECK (type IN ('LIKE', 'HEART', 'LAUGH', 'WOW', 'SAD', 'SUPPORT'))
);

CREATE TABLE comment_reactions (
    comment_id uuid NOT NULL REFERENCES comments (id),
    user_id uuid NOT NULL REFERENCES users (id),
    type varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id),
    CONSTRAINT comment_reactions_type_known CHECK (type IN ('LIKE', 'HEART', 'LAUGH', 'WOW', 'SAD', 'SUPPORT'))
);

-- Подсчёт по типам для одного объекта — основной путь чтения счётчиков.
CREATE INDEX post_reactions_post_idx ON post_reactions (post_id);
CREATE INDEX comment_reactions_comment_idx ON comment_reactions (comment_id);
