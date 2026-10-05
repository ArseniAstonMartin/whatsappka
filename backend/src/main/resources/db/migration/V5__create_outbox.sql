-- Transactional outbox (PRD §5.3): событие пишется в той же транзакции, что и изменение данных.
-- Доставка по consumer ведётся отдельно, поэтому повтор не повторяет уже успешных получателей.

CREATE TABLE outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type varchar(40) NOT NULL,
    aggregate_id uuid NOT NULL,
    type varchar(80) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    available_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    failed_at timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    lease_owner varchar(100),
    lease_until timestamptz,
    last_error text,
    CONSTRAINT outbox_events_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT outbox_events_lease_pair CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    CONSTRAINT outbox_events_not_both_final CHECK (processed_at IS NULL OR failed_at IS NULL)
);

CREATE INDEX outbox_events_pending_idx ON outbox_events (available_at, occurred_at)
    WHERE processed_at IS NULL AND failed_at IS NULL;

CREATE TABLE outbox_deliveries (
    event_id uuid NOT NULL REFERENCES outbox_events (id),
    consumer varchar(100) NOT NULL,
    delivered_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer)
);
