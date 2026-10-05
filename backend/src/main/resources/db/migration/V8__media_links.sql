-- Привязки медиа к объектам. Доступ к файлу определяется только привязками и правами владельца объекта.
-- Типы связей перечислены явно: неизвестный тип в БД не записывается, а в доступе запрещён по умолчанию.

CREATE TABLE media_links (
    media_id uuid NOT NULL REFERENCES media_assets (id),
    link_type varchar(30) NOT NULL,
    link_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (media_id, link_type, link_id),
    CONSTRAINT media_links_type_allowed CHECK (link_type IN ('PROFILE_AVATAR', 'PROFILE_COVER'))
);

CREATE INDEX media_links_target_idx ON media_links (link_type, link_id);
