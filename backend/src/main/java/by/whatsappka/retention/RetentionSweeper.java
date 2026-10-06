package by.whatsappka.retention;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Плановая очистка в процессе worker. Каждая фаза — короткая транзакция под advisory-блокировкой: если другой
 * worker уже чистит, фаза пропускается. В журнал пишутся только числа, без содержимого.
 */
@Component
@ConditionalOnProperty(prefix = "whatsappka.jobs", name = "worker-enabled", havingValue = "true")
public class RetentionSweeper {

    static final long LOCK_KEY = 7_304_400L;
    static final Duration EVENTS_AGE = Duration.ofDays(7);
    static final Duration JOBS_AGE = Duration.ofDays(7);
    static final Duration NOTIFICATIONS_AGE = Duration.ofDays(90);
    static final Duration EVIDENCE_AGE = Duration.ofDays(180);

    public record Report(long idempotency, long jobs, long notifications, long refreshTokens, long sessions,
                         long accountTokens, long evidence, long reservations, long audit, long events,
                         int orphanUploads, int mediaPurged, int mediaDeferred) {
    }

    private static final Logger log = LoggerFactory.getLogger(RetentionSweeper.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MediaRetention media;
    private final Clock clock;

    public RetentionSweeper(JdbcTemplate jdbc, PlatformTransactionManager transactions, MediaRetention media, Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.media = media;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 180_000, fixedDelay = 3_600_000)
    public void sweep() {
        try {
            Report report = runOnce();
            log.info("Очистка выполнена: {}", report);
        } catch (RuntimeException e) {
            // Сбой очистки не должен остановить worker: следующий проход повторит фазы.
            log.warn("Очистка прервана: {}", e.getClass().getSimpleName());
        }
    }

    public Report runOnce() {
        Instant now = clock.instant();
        long[] records = tx.execute(status -> recordPhase(now));
        long[] events = tx.execute(status -> eventPhase(now));
        int orphans = media.markOrphans();
        MediaRetention.Result purged = media.purgeDeleted();
        return new Report(records[0], records[1], records[2], records[3], records[4], records[5], records[6],
                records[7], records[8], events[0], orphans, purged.purged(), purged.deferred());
    }

    private long[] recordPhase(Instant now) {
        if (!locked()) {
            return new long[9];
        }
        long idempotency = jdbc.update(RetentionSql.IDEMPOTENCY, ts(now));
        long jobs = jdbc.update(RetentionSql.FINISHED_JOBS, ts(now.minus(JOBS_AGE)));
        long notifications = jdbc.update(RetentionSql.NOTIFICATIONS, ts(now.minus(NOTIFICATIONS_AGE)));
        long refresh = jdbc.update(RetentionSql.REFRESH_TOKENS, ts(now));
        long sessions = jdbc.update(RetentionSql.SESSIONS, ts(now));
        long accountTokens = jdbc.update(RetentionSql.ACCOUNT_TOKENS, ts(now));
        long evidence = jdbc.update(RetentionSql.REPORT_EVIDENCE, ts(now.minus(EVIDENCE_AGE)));
        long reservations = jdbc.update(RetentionSql.RESERVATIONS, ts(now));
        Long audit = jdbc.queryForObject(RetentionSql.AUDIT, Long.class, ts(now.minus(EVIDENCE_AGE)));
        return new long[] {idempotency, jobs, notifications, refresh, sessions, accountTokens, evidence, reservations,
                audit == null ? 0 : audit};
    }

    /** Сначала поднимается граница курса, потом удаляются события: курс остаётся согласованным. */
    private long[] eventPhase(Instant now) {
        if (!locked()) {
            return new long[1];
        }
        Timestamp cutoff = ts(now.minus(EVENTS_AGE));
        jdbc.update(RetentionSql.EVENT_FLOOR, cutoff);
        long events = jdbc.update(RetentionSql.EVENTS, cutoff);
        return new long[] {events};
    }

    private boolean locked() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
