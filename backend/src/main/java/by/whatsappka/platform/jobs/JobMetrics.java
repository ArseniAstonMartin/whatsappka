package by.whatsappka.platform.jobs;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Состояние очереди для метрик. Считается раз в несколько секунд, а не на каждый скрейп: запросы идут по индексу
 * (status, run_at), таблица заданий растёт, и полный подсчёт не нужен.
 */
@Component
@ConditionalOnProperty(prefix = "whatsappka.jobs", name = "worker-enabled", havingValue = "true")
public class JobMetrics {

    private static final Logger log = LoggerFactory.getLogger(JobMetrics.class);

    private final JdbcTemplate jdbc;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    public JobMetrics(JdbcTemplate jdbc, MeterRegistry metrics) {
        this.jdbc = jdbc;
        Gauge.builder("whatsappka.jobs.queued", snapshot, s -> s.get().queued()).register(metrics);
        Gauge.builder("whatsappka.jobs.running", snapshot, s -> s.get().running()).register(metrics);
        Gauge.builder("whatsappka.jobs.failed", snapshot, s -> s.get().failed()).register(metrics);
        Gauge.builder("whatsappka.jobs.oldest.queued.seconds", snapshot, s -> s.get().oldestQueuedSeconds()).register(metrics);
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 5000)
    public void refresh() {
        try {
            snapshot.set(load());
        } catch (RuntimeException e) {
            // Метрики не должны ронять worker; старые значения остаются до следующей попытки.
            log.warn("Метрики очереди не обновлены: {}", e.getClass().getSimpleName());
        }
    }

    private Snapshot load() {
        long[] counts = new long[3];
        jdbc.query(JobMetricsSql.COUNTS_BY_STATUS, rs -> {
            switch (rs.getString("status")) {
                case "QUEUED" -> counts[0] = rs.getLong("n");
                case "RUNNING" -> counts[1] = rs.getLong("n");
                case "FAILED" -> counts[2] = rs.getLong("n");
                default -> { }
            }
        });
        Double oldest = jdbc.queryForObject(JobMetricsSql.OLDEST_QUEUED_SECONDS, Double.class);
        return new Snapshot(counts[0], counts[1], counts[2], oldest == null ? 0 : oldest);
    }

    record Snapshot(long queued, long running, long failed, double oldestQueuedSeconds) {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0);
    }
}
