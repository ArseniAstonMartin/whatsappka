-- Очередь фоновых заданий: захват с арендой, повторы и статус FAILED (PRD §5.3).

CREATE TABLE background_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    type varchar(40) NOT NULL,
    dedup_key varchar(255) NOT NULL UNIQUE,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    run_at timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    lease_owner varchar(100),
    lease_until timestamptz,
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT background_jobs_status_allowed CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT background_jobs_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT background_jobs_running_has_lease CHECK (status <> 'RUNNING' OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL))
);

CREATE INDEX background_jobs_status_run_at_idx ON background_jobs (status, run_at);
