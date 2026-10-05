-- Учётные записи, профили, роли, сессии, токены и аудит.
-- FK на media_assets добавляется миграцией медиа (аватар и обложка пока только uuid).

CREATE TABLE roles (
    code varchar(30) PRIMARY KEY,
    CONSTRAINT roles_code_allowed CHECK (code IN ('USER', 'MODERATOR', 'ADMIN'))
);

INSERT INTO roles (code) VALUES ('USER'), ('MODERATOR'), ('ADMIN');

CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email varchar(254) NOT NULL,
    username varchar(30) NOT NULL,
    password_hash varchar(100),
    email_verified_at timestamptz,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    last_active_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_email_length CHECK (char_length(email) BETWEEN 3 AND 254),
    CONSTRAINT users_username_length CHECK (char_length(username) BETWEEN 3 AND 30),
    CONSTRAINT users_status_allowed CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));
CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));

CREATE TABLE user_profiles (
    user_id uuid PRIMARY KEY REFERENCES users (id),
    display_name varchar(80) NOT NULL,
    bio varchar(500) NOT NULL DEFAULT '',
    status_text varchar(140) NOT NULL DEFAULT '',
    avatar_media_id uuid,
    cover_media_id uuid,
    verified_at timestamptz,
    verified_by uuid REFERENCES users (id),
    timezone varchar(64) NOT NULL DEFAULT 'UTC',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_profiles_display_name_length CHECK (char_length(display_name) BETWEEN 1 AND 80),
    CONSTRAINT user_profiles_verification_pair CHECK ((verified_at IS NULL) = (verified_by IS NULL))
);

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users (id),
    role_code varchar(30) NOT NULL REFERENCES roles (code),
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_code)
);

CREATE INDEX user_roles_role_code_idx ON user_roles (role_code);

CREATE TABLE external_identities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id),
    provider varchar(20) NOT NULL,
    subject varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT external_identities_provider_allowed CHECK (provider IN ('GOOGLE')),
    CONSTRAINT external_identities_provider_subject_key UNIQUE (provider, subject)
);

CREATE INDEX external_identities_user_id_idx ON external_identities (user_id);

CREATE TABLE auth_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id),
    absolute_expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    device_label varchar(150) NOT NULL,
    last_used_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT auth_sessions_expiry_after_creation CHECK (absolute_expires_at > created_at)
);

CREATE INDEX auth_sessions_user_id_idx ON auth_sessions (user_id);

CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id uuid NOT NULL REFERENCES auth_sessions (id),
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    replaced_by_id uuid UNIQUE REFERENCES refresh_tokens (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT refresh_tokens_token_hash_hex CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX refresh_tokens_session_id_idx ON refresh_tokens (session_id);

CREATE TABLE account_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id),
    purpose varchar(30) NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT account_tokens_purpose_allowed CHECK (purpose IN ('EMAIL_CONFIRMATION', 'PASSWORD_RESET')),
    CONSTRAINT account_tokens_token_hash_hex CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX account_tokens_user_id_idx ON account_tokens (user_id);

CREATE TABLE audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid REFERENCES users (id),
    action varchar(80) NOT NULL,
    target_type varchar(40) NOT NULL,
    target_id uuid,
    reason text,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    trace_id varchar(64) NOT NULL
);

CREATE INDEX audit_logs_occurred_at_idx ON audit_logs (occurred_at);

-- Журнал только дописывается: приложение не меняет и не удаляет записи.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'whatsappka_app') THEN
        REVOKE UPDATE, DELETE ON TABLE audit_logs FROM whatsappka_app;
    END IF;
END $$;

ALTER TABLE idempotency_records
    ADD CONSTRAINT idempotency_records_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);
