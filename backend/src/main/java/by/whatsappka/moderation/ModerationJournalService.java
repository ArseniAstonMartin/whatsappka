package by.whatsappka.moderation;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Журнал решений: что сделано, кем, когда и с какой причиной. Содержимое объектов сюда не попадает —
 * только идентификатор жалобы и вид действия, так что журнал не открывает чужой чат или пост.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ModerationJournalService {

    public record JournalEntry(UUID id, UUID reportId, String action, String fromStatus, String toStatus,
                               String reason, String actorUsername, Instant createdAt) {
    }

    private static final String SELECT = """
            SELECT ma.id, ma.report_id, ma.action, ma.from_status, ma.to_status, ma.reason, ma.created_at, u.username
            FROM moderation_actions ma JOIN users u ON u.id = ma.actor_id
            """;

    private static final RowMapper<JournalEntry> ROW = (rs, n) -> new JournalEntry(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("report_id")),
            rs.getString("action"),
            rs.getString("from_status"),
            rs.getString("to_status"),
            rs.getString("reason"),
            rs.getString("username"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public ModerationJournalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Новые записи первыми; при равном времени — по id. Фильтр по жалобе необязателен. */
    @Transactional(readOnly = true)
    public CursorPage<JournalEntry> journal(UUID reportId, String cursor, Integer limit) {
        int size = PageSize.limit(limit);
        Keyset key = decode(cursor);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        if (reportId != null) {
            sql.append(" AND ma.report_id = ?");
            params.add(reportId);
        }
        if (key != null) {
            sql.append(" AND (ma.created_at, ma.id) < (CAST(? AS timestamptz), CAST(? AS uuid))");
            params.add(Timestamp.from(key.at()));
            params.add(key.id());
        }
        sql.append(" ORDER BY ma.created_at DESC, ma.id DESC LIMIT ?");
        params.add(size + 1);
        List<JournalEntry> rows = jdbc.query(sql.toString(), ROW, params.toArray());
        boolean more = rows.size() > size;
        List<JournalEntry> shown = new ArrayList<>(more ? rows.subList(0, size) : rows);
        String next = more ? encode(shown.get(shown.size() - 1).createdAt(), shown.get(shown.size() - 1).id()) : null;
        return new CursorPage<>(shown, next, more);
    }

    private record Keyset(Instant at, UUID id) {
    }

    static String encode(Instant at, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((at.toString() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    static Keyset decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            return new Keyset(Instant.parse(raw.substring(0, separator)), UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException malformed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_cursor", "Курсор страницы некорректен", List.of(), null);
        }
    }
}
