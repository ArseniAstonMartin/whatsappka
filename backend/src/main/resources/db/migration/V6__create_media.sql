-- Медиа: исходные файлы и производные варианты (PRD §6, FR-10).
-- Файлы живут в закрытом bucket MinIO; в PostgreSQL только метаданные и ключи объектов.

CREATE TABLE media_assets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id uuid NOT NULL REFERENCES users (id),
    original_object_key varchar(500) NOT NULL UNIQUE,
    filename varchar(255) NOT NULL,
    detected_mime varchar(100),
    size_bytes bigint NOT NULL,
    width integer,
    height integer,
    status varchar(20) NOT NULL,
    failure_code varchar(100),
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT media_assets_status_allowed CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT media_assets_size_non_negative CHECK (size_bytes >= 0),
    CONSTRAINT media_assets_dimensions_positive CHECK (
        (width IS NULL OR width > 0) AND (height IS NULL OR height > 0)
    ),
    CONSTRAINT media_assets_failure_only_when_failed CHECK (failure_code IS NULL OR status = 'FAILED')
);

CREATE INDEX media_assets_owner_id_idx ON media_assets (owner_id);

CREATE TABLE media_variants (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id uuid NOT NULL REFERENCES media_assets (id),
    kind varchar(20) NOT NULL,
    object_key varchar(500) NOT NULL UNIQUE,
    size_bytes bigint NOT NULL,
    width integer,
    height integer,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT media_variants_kind_allowed CHECK (kind IN ('SIZE_320', 'SIZE_1280')),
    CONSTRAINT media_variants_size_non_negative CHECK (size_bytes >= 0),
    CONSTRAINT media_variants_media_kind_key UNIQUE (media_id, kind)
);

-- Аватар и обложка ссылаются на медиа; до появления таблицы их не было.
ALTER TABLE user_profiles
    ADD CONSTRAINT user_profiles_avatar_media_fkey FOREIGN KEY (avatar_media_id) REFERENCES media_assets (id),
    ADD CONSTRAINT user_profiles_cover_media_fkey FOREIGN KEY (cover_media_id) REFERENCES media_assets (id);
