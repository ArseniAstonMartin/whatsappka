package by.whatsappka.platform.outbox;

/** SQL outbox. Константы вынесены, чтобы их можно было проверить отдельно от Spring. */
final class OutboxSql {

    private OutboxSql() {
    }

    static final String INSERT = """
            INSERT INTO outbox_events (id, aggregate_type, aggregate_id, type, payload, occurred_at, available_at, attempts)
            VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, 0)
            """;

    static final String FAIL_EXHAUSTED_LEASES = """
            UPDATE outbox_events
            SET failed_at = now(), lease_owner = NULL, lease_until = NULL,
                last_error = 'Аренда истекла, попытки исчерпаны'
            WHERE processed_at IS NULL AND failed_at IS NULL AND lease_until < now() AND attempts >= ?
            """;

    static final String CLAIM = """
            WITH picked AS (
                SELECT id FROM outbox_events
                WHERE processed_at IS NULL AND failed_at IS NULL
                  AND available_at <= now()
                  AND (lease_until IS NULL OR lease_until < now())
                  AND attempts < ?
                ORDER BY occurred_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_events e
            SET lease_owner = ?, lease_until = now() + (? * interval '1 second'), attempts = e.attempts + 1
            FROM picked
            WHERE e.id = picked.id
            RETURNING e.id, e.aggregate_type, e.aggregate_id, e.type,
                      CAST(e.payload AS text) AS payload, e.occurred_at, e.attempts
            """;

    static final String DELIVERED_CONSUMERS = """
            SELECT consumer FROM outbox_deliveries WHERE event_id = ?
            """;

    static final String RECORD_DELIVERY = """
            INSERT INTO outbox_deliveries (event_id, consumer, delivered_at)
            VALUES (?, ?, now())
            ON CONFLICT (event_id, consumer) DO NOTHING
            """;

    static final String COMPLETE = """
            UPDATE outbox_events
            SET processed_at = now(), lease_owner = NULL, lease_until = NULL, last_error = NULL
            WHERE id = ? AND lease_owner = ? AND processed_at IS NULL AND failed_at IS NULL
            """;

    static final String RETRY_LATER = """
            UPDATE outbox_events
            SET lease_owner = NULL, lease_until = NULL, last_error = ?,
                available_at = now() + (? * interval '1 second')
            WHERE id = ? AND lease_owner = ? AND processed_at IS NULL AND failed_at IS NULL
            """;

    static final String FAIL = """
            UPDATE outbox_events
            SET failed_at = now(), lease_owner = NULL, lease_until = NULL, last_error = ?
            WHERE id = ? AND lease_owner = ? AND processed_at IS NULL AND failed_at IS NULL
            """;
}
