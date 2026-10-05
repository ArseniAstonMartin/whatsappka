package by.whatsappka.notifications;

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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Список собственных уведомлений. Подробности объекта и актора отдаются только если текущий доступ их разрешает:
 * у недоступного объекта ссылка скрыта, у заблокированного актора — тоже. Тело переписки не отдаётся никогда.
 */
@Service
public class NotificationQueries {

    public record Actor(UUID id, String username, String displayName) {
    }

    public record Target(NotificationType.TargetKind kind, UUID id) {
    }

    public record NotificationView(
            UUID id,
            NotificationType type,
            Instant createdAt,
            boolean read,
            Actor actor,
            Target target
    ) {
    }

    private static final RowMapper<Row> ROW = (rs, n) -> new Row(
            UUID.fromString(rs.getString("id")),
            NotificationRules.parseType(rs.getString("type")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("read_at") != null,
            rs.getObject("actor_id") == null ? null : UUID.fromString(rs.getString("actor_id")),
            rs.getString("actor_username"),
            rs.getString("actor_display_name"),
            rs.getBoolean("actor_blocked"),
            rs.getString("target_kind"),
            rs.getObject("target_id") == null ? null : UUID.fromString(rs.getString("target_id")),
            rs.getBoolean("target_visible"));

    private final JdbcTemplate jdbc;

    public NotificationQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CursorPage<NotificationView> list(UUID viewer, String cursor, Integer limit) {
        int size = PageSize.limit(limit);
        Keyset key = decode(cursor);
        List<Row> rows = key == null
                ? jdbc.query(NotificationSql.LIST_FIRST, ROW, viewer, size + 1)
                : jdbc.query(NotificationSql.LIST_AFTER, ROW, viewer, Timestamp.from(key.at()), key.id(), size + 1);
        boolean more = rows.size() > size;
        List<Row> shown = more ? rows.subList(0, size) : rows;
        List<NotificationView> items = new ArrayList<>(shown.size());
        for (Row row : shown) {
            items.add(row.toView());
        }
        String next = more ? encode(shown.get(shown.size() - 1).createdAt(), shown.get(shown.size() - 1).id()) : null;
        return new CursorPage<>(items, next, more);
    }

    private record Row(
            UUID id,
            NotificationType type,
            Instant createdAt,
            boolean read,
            UUID actorId,
            String actorUsername,
            String actorDisplayName,
            boolean actorBlocked,
            String targetKind,
            UUID targetId,
            boolean targetVisible
    ) {
        NotificationView toView() {
            Actor actor = actorId == null || actorBlocked
                    ? null
                    : new Actor(actorId, actorUsername, actorDisplayName);
            Target target = targetId == null || !targetVisible
                    ? null
                    : new Target(NotificationType.TargetKind.valueOf(targetKind), targetId);
            return new NotificationView(id, type, createdAt, read, actor, target);
        }
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_cursor", "Курсор страницы некорректен",
                    List.of(), null);
        }
    }
}
