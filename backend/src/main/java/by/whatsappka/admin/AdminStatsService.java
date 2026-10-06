package by.whatsappka.admin;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Агрегаты для администратора: только числа из базы, без текстов, имён и содержимого переписки.
 * Период в UTC, не длиннее {@link #MAX_DAYS} дней и не в будущем; по умолчанию — последние 7 дней.
 */
@Service
public class AdminStatsService {

    static final int MAX_DAYS = 90;
    static final int DEFAULT_DAYS = 7;

    static final String USERS = "SELECT count(*) FROM users";

    static final String ACTIVE_ON_DAY = "SELECT count(*) FROM user_activity_days WHERE day = ?";

    static final String POSTS_PUBLISHED = """
            SELECT count(*) FROM posts
            WHERE status = 'PUBLISHED' AND deleted_at IS NULL AND published_at >= ? AND published_at < ?
            """;

    static final String MESSAGES_SENT = "SELECT count(*) FROM messages WHERE created_at >= ? AND created_at < ?";

    static final String GROUPS = "SELECT count(*) FROM groups WHERE deleted_at IS NULL";

    static final String OPEN_REPORTS = "SELECT count(*) FROM reports WHERE status IN ('OPEN', 'IN_REVIEW')";

    static final String MEDIA_BYTES = """
            SELECT COALESCE((SELECT SUM(a.size_bytes) FROM media_assets a WHERE a.deleted_at IS NULL), 0)
                 + COALESCE((SELECT SUM(v.size_bytes) FROM media_variants v
                             JOIN media_assets a ON a.id = v.media_id WHERE a.deleted_at IS NULL), 0)
            """;

    public record Period(LocalDate from, LocalDate to) {
    }

    public record Stats(
            LocalDate from,
            LocalDate to,
            long users,
            long dailyActiveUsers,
            long postsPublished,
            long messagesSent,
            long groups,
            long openReports,
            long mediaBytes
    ) {
    }

    private final JdbcTemplate jdbc;

    public AdminStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Проверка периода отдельно от запроса: правила видны и проверяются без базы. */
    public static Period period(String from, String to, LocalDate today) {
        LocalDate end = parseOrDefault(to, today, "to");
        LocalDate start = parseOrDefault(from, end.minusDays(DEFAULT_DAYS - 1L), "from");
        if (end.isAfter(today)) {
            throw invalid("Конец периода не может быть в будущем");
        }
        if (start.isAfter(end)) {
            throw invalid("Начало периода позже конца");
        }
        if (end.toEpochDay() - start.toEpochDay() + 1 > MAX_DAYS) {
            throw invalid("Период не длиннее " + MAX_DAYS + " дней");
        }
        return new Period(start, end);
    }

    @Transactional(readOnly = true)
    public Stats stats(Period period) {
        Timestamp startTs = Timestamp.from(period.from().atStartOfDay(ZoneOffset.UTC).toInstant());
        Timestamp endTs = Timestamp.from(period.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        return new Stats(
                period.from(),
                period.to(),
                count(USERS),
                jdbc.queryForObject(ACTIVE_ON_DAY, Long.class, Date.valueOf(period.to())),
                jdbc.queryForObject(POSTS_PUBLISHED, Long.class, startTs, endTs),
                jdbc.queryForObject(MESSAGES_SENT, Long.class, startTs, endTs),
                count(GROUPS),
                count(OPEN_REPORTS),
                jdbc.queryForObject(MEDIA_BYTES, Long.class)
        );
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static LocalDate parseOrDefault(String raw, LocalDate fallback, String field) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail(field, "Дата в формате ГГГГ-ММ-ДД")));
        }
    }

    private static ApiException invalid(String detail) {
        return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid_period", detail, List.of(), null);
    }
}
