package by.whatsappka.platform.jobs;

/**
 * SQL очереди. Вынесен в константы, чтобы его можно было проверить отдельно от Spring.
 * Каждое обновление состояния требует совпадения владельца аренды, кроме операторского повтора.
 */
final class BackgroundJobSql {

    private BackgroundJobSql() {
    }

    static final String ENQUEUE = """
            INSERT INTO background_jobs (id, type, dedup_key, payload, run_at, status, attempts, created_at, updated_at)
            VALUES (?, ?, ?, CAST(? AS jsonb), ?, 'QUEUED', 0, now(), now())
            ON CONFLICT (dedup_key) DO NOTHING
            """;

    /** Задания, у которых аренда истекла и попытки исчерпаны, фиксируются как FAILED до захвата. */
    static final String FAIL_EXHAUSTED_LEASES = """
            UPDATE background_jobs
            SET status = 'FAILED', lease_owner = NULL, lease_until = NULL,
                last_error = 'Аренда истекла, попытки исчерпаны', updated_at = now()
            WHERE status = 'RUNNING' AND lease_until < now() AND attempts >= ?
            """;

    /** Короткая транзакция захвата: очередь, либо аренда, которую бросил упавший worker. */
    static final String CLAIM = """
            WITH picked AS (
                SELECT id FROM background_jobs
                WHERE (status = 'QUEUED' AND run_at <= now())
                   OR (status = 'RUNNING' AND lease_until < now() AND attempts < ?)
                ORDER BY run_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            UPDATE background_jobs j
            SET status = 'RUNNING', lease_owner = ?, lease_until = now() + (? * interval '1 second'),
                attempts = j.attempts + 1, updated_at = now()
            FROM picked
            WHERE j.id = picked.id
            RETURNING j.id, j.type, CAST(j.payload AS text) AS payload, j.attempts
            """;

    static final String COMPLETE = """
            UPDATE background_jobs
            SET status = 'DONE', lease_owner = NULL, lease_until = NULL, last_error = NULL, updated_at = now()
            WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
            """;

    static final String RETRY_LATER = """
            UPDATE background_jobs
            SET status = 'QUEUED', lease_owner = NULL, lease_until = NULL, last_error = ?,
                run_at = now() + (? * interval '1 second'), updated_at = now()
            WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
            """;

    static final String FAIL = """
            UPDATE background_jobs
            SET status = 'FAILED', lease_owner = NULL, lease_until = NULL, last_error = ?, updated_at = now()
            WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
            """;

    /** Операторский повтор: только для FAILED, попытки начинаются заново. */
    static final String OPERATOR_RETRY = """
            UPDATE background_jobs
            SET status = 'QUEUED', attempts = 0, run_at = now(), last_error = NULL,
                lease_owner = NULL, lease_until = NULL, updated_at = now()
            WHERE id = ? AND status = 'FAILED'
            """;
}
