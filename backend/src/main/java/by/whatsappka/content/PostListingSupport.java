package by.whatsappka.content;

import by.whatsappka.platform.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Общий запрос, маппер строки и keyset-курсор ({@code published_at, id}) для публичных списков
 * постов — ленты, профиля, группы и хештега (TASK-044, TASK-045, TASK-047).
 *
 * <p>Картинки и хештеги читаются коррелированными подзапросами, а не JOIN + GROUP BY: при нескольких
 * LEFT JOIN на зависимые таблицы (вложения и хештеги одновременно) строки размножаются перекрёстно,
 * и агрегаты без дополнительного DISTINCT/сортировки по каждому считаются неверно — подзапрос этого
 * избегает и к тому же сохраняет порядок вложений по {@code position}.
 */
final class PostListingSupport {

    private PostListingSupport() {
    }

    /**
     * {@code whereExtra} — условие источника (своя лента/профиль/группа/хештег), уже включает проверку
     * видимости; {@code status = 'PUBLISHED' AND deleted_at IS NULL} добавлены здесь, их дублировать не нужно.
     *
     * <p>Первый параметр запроса — всегда {@code viewerId} для подзапроса {@code viewer_reaction}
     * (TASK-051): он стоит в SELECT раньше любого параметра из {@code whereExtra}, поэтому вызывающая
     * сторона передаёт его первым в массиве параметров.
     */
    static String selectSql(String whereExtra, boolean hasCursor) {
        return "SELECT p.id, p.body, p.author_id, u.username AS author_username, "
                + "pr.display_name AS author_display_name, pr.avatar_media_id AS author_avatar_media_id, "
                + "p.group_id, p.published_at, p.updated_at, "
                + "(SELECT array_agg(pm.media_id ORDER BY pm.position) FROM post_media pm WHERE pm.post_id = p.id) AS media_ids, "
                + "(SELECT array_agg(h.normalized_name ORDER BY h.normalized_name) FROM post_hashtags ph "
                + "    JOIN hashtags h ON h.id = ph.hashtag_id WHERE ph.post_id = p.id) AS hashtags, "
                + "(SELECT string_agg(rc.type || ':' || rc.c, ',') FROM "
                + "    (SELECT type, count(*) AS c FROM post_reactions WHERE post_id = p.id GROUP BY type) rc) AS reaction_summary, "
                + "(SELECT type FROM post_reactions WHERE post_id = p.id AND user_id = ?) AS viewer_reaction "
                + "FROM posts p "
                + "JOIN users u ON u.id = p.author_id "
                + "JOIN user_profiles pr ON pr.user_id = u.id "
                + "WHERE p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND " + whereExtra + " "
                + (hasCursor ? "AND (p.published_at, p.id) < (?, ?) " : "")
                + "ORDER BY p.published_at DESC, p.id DESC LIMIT ?";
    }

    static final RowMapper<PostSummaryPublic> ROW_MAPPER = (rs, n) -> new PostSummaryPublic(
            UUID.fromString(rs.getString("id")),
            rs.getString("body"),
            UUID.fromString(rs.getString("author_id")),
            rs.getString("author_username"),
            rs.getString("author_display_name"),
            rs.getObject("author_avatar_media_id") == null ? null : UUID.fromString(rs.getString("author_avatar_media_id")),
            rs.getObject("group_id") == null ? null : UUID.fromString(rs.getString("group_id")),
            toUuidList(rs.getArray("media_ids")),
            toStringList(rs.getArray("hashtags")),
            rs.getTimestamp("published_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            toReactionCounts(rs.getString("reaction_summary")),
            rs.getString("viewer_reaction"));

    private static List<UUID> toUuidList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (Object value : (Object[]) array.getArray()) {
            if (value != null) {
                result.add(UUID.fromString(value.toString()));
            }
        }
        return result;
    }

    /** {@code "LIKE:3,HEART:1"} → карта типов и счётчиков; пусто, если реакций ещё нет. */
    private static Map<String, Long> toReactionCounts(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String part : raw.split(",")) {
            int separator = part.lastIndexOf(':');
            counts.put(part.substring(0, separator), Long.parseLong(part.substring(separator + 1)));
        }
        return counts;
    }

    private static List<String> toStringList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object value : (Object[]) array.getArray()) {
            if (value != null) {
                result.add(value.toString());
            }
        }
        return result;
    }

    record Keyset(Instant at, UUID id) {
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
            throw ApiException.badRequest("invalid_cursor", "Курсор страницы некорректен");
        }
    }
}
