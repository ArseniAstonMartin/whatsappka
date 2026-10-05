package by.whatsappka.platform.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Захват, выполнение и завершение заданий. Захват и завершение идут короткими транзакциями,
 * сам обработчик выполняется вне транзакции. Завершение проверяет владельца аренды:
 * если аренду забрал другой worker, результат этого процесса не записывается.
 */
@Component
@ConditionalOnProperty(prefix = "whatsappka.jobs", name = "worker-enabled", havingValue = "true")
public class JobWorker {

    /** Число попыток, после которого задание становится FAILED. */
    static final int MAX_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private static final int REASON_MAX = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Map<String, JobHandler> handlers;
    private final ThreadPoolTaskExecutor executor;
    private final JobProperties properties;
    private final String workerId;
    private final AtomicInteger inFlight = new AtomicInteger();

    public JobWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            ObjectMapper mapper,
            List<JobHandler> handlers,
            ThreadPoolTaskExecutor jobExecutor,
            JobProperties properties
    ) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.mapper = mapper;
        this.handlers = indexHandlers(handlers);
        this.executor = jobExecutor;
        this.properties = properties;
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        int free = properties.concurrency() - inFlight.get();
        if (free <= 0) {
            return;
        }
        List<Claimed> claimed = tx.execute(status -> {
            jdbc.update(BackgroundJobSql.FAIL_EXHAUSTED_LEASES, MAX_ATTEMPTS);
            return claim(free);
        });
        for (Claimed job : claimed) {
            inFlight.incrementAndGet();
            executor.execute(() -> run(job));
        }
    }

    private List<Claimed> claim(int limit) {
        return jdbc.query(
                BackgroundJobSql.CLAIM,
                (rs, row) -> new Claimed(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("type"),
                        rs.getString("payload"),
                        rs.getInt("attempts")
                ),
                MAX_ATTEMPTS,
                limit,
                workerId,
                leaseSeconds()
        );
    }

    private void run(Claimed job) {
        try {
            JobHandler handler = handlers.get(job.type());
            if (handler == null) {
                finish(BackgroundJobSql.FAIL, job, "Нет обработчика для типа " + job.type());
                return;
            }
            handler.handle(new JobContext(job.id(), mapper.readTree(job.payload())));
            finish(BackgroundJobSql.COMPLETE, job, null);
        } catch (Exception e) {
            String reason = reason(e);
            if (job.attempts() >= MAX_ATTEMPTS) {
                finish(BackgroundJobSql.FAIL, job, reason);
            } else {
                retryLater(job, reason);
            }
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private void retryLater(Claimed job, String reason) {
        long delaySeconds = Duration.ofSeconds(30L << Math.min(job.attempts() - 1, 5)).toSeconds();
        int updated = tx.execute(status -> jdbc.update(
                BackgroundJobSql.RETRY_LATER, reason, delaySeconds, job.id(), workerId));
        logOwnership(job, updated, "повтор через " + delaySeconds + " с");
    }

    /** Завершение (COMPLETE) или фиксация ошибки (FAIL) с проверкой владельца аренды. */
    private void finish(String sql, Claimed job, String reason) {
        int updated = tx.execute(status -> reason == null
                ? jdbc.update(sql, job.id(), workerId)
                : jdbc.update(sql, reason, job.id(), workerId));
        logOwnership(job, updated, reason == null ? "выполнено" : "FAILED");
    }

    private void logOwnership(Claimed job, int updated, String outcome) {
        if (updated == 0) {
            log.warn("Аренда задания {} потеряна, результат ({}) не записан", job.id(), outcome);
        } else {
            log.info("Задание {} ({}): {}", job.id(), job.type(), outcome);
        }
    }

    private long leaseSeconds() {
        return properties.lease().toSeconds();
    }

    private static Map<String, JobHandler> indexHandlers(List<JobHandler> handlers) {
        Map<String, JobHandler> byType = new HashMap<>();
        for (JobHandler handler : handlers) {
            if (byType.putIfAbsent(handler.type(), handler) != null) {
                throw new IllegalStateException("Два обработчика заданий с типом " + handler.type());
            }
        }
        return Map.copyOf(byType);
    }

    private static String reason(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > REASON_MAX ? message.substring(0, REASON_MAX) : message;
    }

    private record Claimed(UUID id, String type, String payload, int attempts) {
    }
}
