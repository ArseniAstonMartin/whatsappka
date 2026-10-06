package by.whatsappka.retention;

import by.whatsappka.media.storage.ObjectStorage;
import by.whatsappka.media.storage.StorageException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Очистка медиа. Сначала удаляются файлы в хранилище, и только потом строки базы: строка остаётся, пока файл
 * не удалён, поэтому сбой MinIO не теряет след и повторяется следующим проходом. Строка удаляется, только если
 * у медиа по-прежнему нет живых ссылок.
 */
@Component
@ConditionalOnProperty(prefix = "whatsappka.jobs", name = "worker-enabled", havingValue = "true")
public class MediaRetention {

    static final Duration ORPHAN_AGE = Duration.ofHours(24);
    static final int BATCH = 50;

    private static final Logger log = LoggerFactory.getLogger(MediaRetention.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectStorage storage;
    private final Clock clock;

    public MediaRetention(JdbcTemplate jdbc, PlatformTransactionManager transactions, ObjectStorage storage, Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.storage = storage;
        this.clock = clock;
    }

    /** Возвращает число помеченных незакреплённых загрузок. */
    public int markOrphans() {
        Instant now = clock.instant();
        Integer marked = tx.execute(status -> jdbc.update(RetentionSql.MARK_ORPHAN_UPLOADS,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.minus(ORPHAN_AGE))));
        return marked == null ? 0 : marked;
    }

    /** Возвращает число удалённых медиа; ошибки хранилища считаются и откладываются. */
    public Result purgeDeleted() {
        List<Candidate> candidates = jdbc.query(RetentionSql.DELETED_MEDIA,
                (rs, n) -> new Candidate(UUID.fromString(rs.getString("id")), rs.getString("original_object_key")), BATCH);
        int purged = 0;
        int deferred = 0;
        for (Candidate candidate : candidates) {
            if (purgeOne(candidate)) {
                purged++;
            } else {
                deferred++;
            }
        }
        return new Result(purged, deferred);
    }

    private boolean purgeOne(Candidate candidate) {
        List<String> keys = new ArrayList<>();
        keys.add(candidate.originalKey());
        keys.addAll(jdbc.queryForList(RetentionSql.VARIANT_KEYS, String.class, candidate.id()));
        try {
            for (String key : keys) {
                storage.delete(key);
            }
        } catch (StorageException e) {
            // Файлы не удалены: строку не трогаем, следующий проход повторит попытку.
            log.warn("Файл медиа не удалён, повтор в следующем проходе: {}", e.getClass().getSimpleName());
            return false;
        }
        Boolean removed = tx.execute(status -> {
            Integer unreferenced = jdbc.queryForObject(RetentionSql.STILL_UNREFERENCED, Integer.class, candidate.id());
            if (unreferenced == null || unreferenced == 0) {
                return false;
            }
            jdbc.update(RetentionSql.DELETE_VARIANT_ROWS, candidate.id());
            return jdbc.update(RetentionSql.DELETE_MEDIA_ROW, candidate.id()) == 1;
        });
        return Boolean.TRUE.equals(removed);
    }

    private record Candidate(UUID id, String originalKey) {
    }

    public record Result(int purged, int deferred) {
    }
}
