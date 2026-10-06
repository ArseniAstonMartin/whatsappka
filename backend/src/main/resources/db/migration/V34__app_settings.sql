-- Настройки приложения, которые меняет администратор. Ключи ограничены: новые настройки добавляются миграцией.
CREATE TABLE app_settings (
    key varchar(40) PRIMARY KEY,
    value varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid REFERENCES users (id),
    CONSTRAINT app_settings_key_allowed CHECK (key IN (
        'registration_open', 'media_upload_limit_bytes', 'media_user_quota_bytes', 'site_name'))
);
