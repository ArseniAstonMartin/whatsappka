package by.whatsappka.platform.jobs;

/** Запросы метрик очереди. Оба опираются на индекс background_jobs (status, run_at). */
final class JobMetricsSql {

    private JobMetricsSql() {
    }

    static final String COUNTS_BY_STATUS = """
            SELECT status, count(*) AS n
            FROM background_jobs
            WHERE status IN ('QUEUED', 'RUNNING', 'FAILED')
            GROUP BY status
            """;

    static final String OLDEST_QUEUED_SECONDS = """
            SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(run_at))), 0)
            FROM background_jobs
            WHERE status = 'QUEUED' AND run_at <= now()
            """;
}
