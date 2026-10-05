package by.whatsappka.media;

import by.whatsappka.platform.web.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Резервирует место под загрузку. Учитываются оригиналы, варианты и незавершённые резервации.
 * Проверки выполняются под advisory-блокировкой, поэтому два параллельных запроса не пройдут оба.
 */
@Component
public class MediaQuota {

    /** Ключ блокировки общей квоты стенда. */
    private static final long GLOBAL_LOCK = 7_304_300L;

    private static final String USED_BY_OWNER = """
            SELECT COALESCE((SELECT SUM(a.size_bytes) FROM media_assets a
                             WHERE a.owner_id = ? AND a.deleted_at IS NULL), 0)
                 + COALESCE((SELECT SUM(v.size_bytes) FROM media_variants v
                             JOIN media_assets a ON a.id = v.media_id
                             WHERE a.owner_id = ? AND a.deleted_at IS NULL), 0)
                 + COALESCE((SELECT SUM(r.size_bytes) FROM media_reservations r
                             WHERE r.owner_id = ? AND r.expires_at > ?), 0)
            """;

    private static final String USED_TOTAL = """
            SELECT COALESCE((SELECT SUM(a.size_bytes) FROM media_assets a WHERE a.deleted_at IS NULL), 0)
                 + COALESCE((SELECT SUM(v.size_bytes) FROM media_variants v
                             JOIN media_assets a ON a.id = v.media_id WHERE a.deleted_at IS NULL), 0)
                 + COALESCE((SELECT SUM(r.size_bytes) FROM media_reservations r WHERE r.expires_at > ?), 0)
            """;

    private final JdbcTemplate jdbc;
    private final MediaLimits limits;
    private final DiskSpaceGuard disk;
    private final Clock clock;

    public MediaQuota(JdbcTemplate jdbc, MediaLimits limits, DiskSpaceGuard disk, Clock clock) {
        this.jdbc = jdbc;
        this.limits = limits;
        this.disk = disk;
        this.clock = clock;
    }

    @Transactional
    public UUID reserve(UUID ownerId, long bytes) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, GLOBAL_LOCK);
        Instant now = clock.instant();
        Timestamp nowTs = Timestamp.from(now);
        long usedByOwner = jdbc.queryForObject(USED_BY_OWNER, Long.class, ownerId, ownerId, ownerId, nowTs);
        if (usedByOwner + bytes > limits.userQuotaBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "quota_exceeded",
                    "Превышена квота медиа", java.util.List.of(), null);
        }
        long usedTotal = jdbc.queryForObject(USED_TOTAL, Long.class, nowTs);
        if (usedTotal + bytes > limits.globalLimitBytes()) {
            throw new ApiException(HttpStatus.INSUFFICIENT_STORAGE, "storage_limit_reached",
                    "Общий лимит медиа стенда исчерпан", java.util.List.of(), null);
        }
        if (!disk.hasRoomForUpload()) {
            throw new ApiException(HttpStatus.INSUFFICIENT_STORAGE, "disk_space_low",
                    "Недостаточно места для новых загрузок", java.util.List.of(), null);
        }
        UUID reservationId = UUID.randomUUID();
        Duration ttl = limits.reservationTtl();
        jdbc.update(
                "INSERT INTO media_reservations (id, owner_id, size_bytes, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                reservationId, ownerId, bytes, nowTs, Timestamp.from(now.plus(ttl)));
        return reservationId;
    }

    /** Снимает резервацию: при сбое загрузки или после фиксации медиа. */
    @Transactional
    public void release(UUID reservationId) {
        jdbc.update("DELETE FROM media_reservations WHERE id = ?", reservationId);
    }
}
