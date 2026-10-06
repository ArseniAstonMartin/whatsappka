package by.whatsappka.identity.audit;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Чтение журнала аудита. Администратор видит всё. Модератор видит только свои решения по жалобам и
 * скрытию контента: остальные записи (роли, блокировки администратора, настройки) ему недоступны, и запрос
 * по ним возвращает пустую страницу, а не отказ, чтобы не раскрывать устройство журнала.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuditQueries {

    /** Действия модератора, которые он видит в собственном журнале. */
    static final Set<String> MODERATOR_ACTIONS = Set.of(
            "REPORT_TAKEN", "REPORT_RESOLVED", "REPORT_REJECTED", "CONTENT_HIDDEN", "SANCTION_ISSUED");

    static final String SELECT = """
            SELECT a.id, u.username, a.action, a.target_type, a.target_id, a.reason, a.occurred_at
            FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_id
            WHERE 1 = 1
            """;

    public record Entry(UUID id, String actor, String action, String targetType, UUID targetId, String reason,
                        Instant occurredAt) {
    }

    public record Filter(String action, String targetType, UUID actorId) {
    }

    private final JdbcTemplate jdbc;

    public AuditQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CursorPage<Entry> page(AuthenticatedUser viewer, Filter filter, String cursor, Integer limit) {
        boolean admin = viewer.roles().contains("ADMIN");
        if (!admin && filter.action() != null && !MODERATOR_ACTIONS.contains(filter.action())) {
            return new CursorPage<>(List.of(), null, false);
        }
        int size = PageSize.limit(limit);
        Keyset key = decode(cursor);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT);
        if (!admin) {
            sql.append(" AND a.actor_id = ? AND a.action IN (")
                    .append(String.join(",", java.util.Collections.nCopies(MODERATOR_ACTIONS.size(), "?"))).append(")");
            params.add(viewer.userId());
            params.addAll(MODERATOR_ACTIONS);
        }
        if (filter.action() != null) {
            sql.append(" AND a.action = ?");
            params.add(filter.action());
        }
        if (filter.targetType() != null) {
            sql.append(" AND a.target_type = ?");
            params.add(filter.targetType());
        }
        if (admin && filter.actorId() != null) {
            sql.append(" AND a.actor_id = ?");
            params.add(filter.actorId());
        }
        if (key != null) {
            sql.append(" AND (a.occurred_at, a.id) < (CAST(? AS timestamptz), CAST(? AS uuid))");
            params.add(Timestamp.from(key.at()));
            params.add(key.id());
        }
        sql.append(" ORDER BY a.occurred_at DESC, a.id DESC LIMIT ?");
        params.add(size + 1);
        List<Entry> rows = jdbc.query(sql.toString(), (rs, n) -> new Entry(
                UUID.fromString(rs.getString("id")),
                rs.getString("username") == null ? "система" : rs.getString("username"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getObject("target_id") == null ? null : UUID.fromString(rs.getString("target_id")),
                rs.getString("reason"),
                rs.getTimestamp("occurred_at").toInstant()), params.toArray());
        boolean more = rows.size() > size;
        List<Entry> shown = new ArrayList<>(more ? rows.subList(0, size) : rows);
        String next = more ? encode(shown.get(shown.size() - 1).occurredAt(), shown.get(shown.size() - 1).id()) : null;
        return new CursorPage<>(shown, next, more);
    }

    private record Keyset(Instant at, UUID id) {
    }

    static String encode(Instant at, UUID id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
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
