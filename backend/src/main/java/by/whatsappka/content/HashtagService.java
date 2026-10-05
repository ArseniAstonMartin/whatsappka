package by.whatsappka.content;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Список публикаций по хештегу (TASK-044). Видимость — {@link PostVisibilitySql}: та же общая
 * политика, которую позже переиспользуют лента (TASK-045) и поиск по хештегам (TASK-072).
 */
@Service
public class HashtagService {

    private final JdbcTemplate jdbc;

    public HashtagService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PostSummaryPublic(UUID id, String body, UUID authorId, UUID groupId, Instant publishedAt) {
    }

    @Transactional(readOnly = true)
    public CursorPage<PostSummaryPublic> posts(UUID viewerId, String rawTag, String cursor, Integer limit) {
        String tag = PostRules.normalizeHashtag(rawTag);
        int size = PageSize.limit(limit);
        Keyset key = decode(cursor);
        String sql = "SELECT p.id, p.body, p.author_id, p.group_id, p.published_at FROM posts p "
                + "JOIN post_hashtags ph ON ph.post_id = p.id "
                + "JOIN hashtags h ON h.id = ph.hashtag_id "
                + "WHERE h.normalized_name = ? AND p.status = 'PUBLISHED' AND p.deleted_at IS NULL "
                + "AND " + PostVisibilitySql.GROUP_VISIBLE_TO_VIEWER
                + (key == null ? "" : "AND (p.published_at, p.id) < (?, ?) ")
                + "ORDER BY p.published_at DESC, p.id DESC LIMIT ?";
        List<Row> rows = key == null
                ? jdbc.query(sql, ROW_MAPPER, tag, viewerId, viewerId, size + 1)
                : jdbc.query(sql, ROW_MAPPER, tag, viewerId, viewerId, Timestamp.from(key.at()), key.id(), size + 1);
        boolean more = rows.size() > size;
        List<Row> shown = more ? rows.subList(0, size) : rows;
        List<PostSummaryPublic> items = shown.stream()
                .map(row -> new PostSummaryPublic(row.id(), row.body(), row.authorId(), row.groupId(), row.publishedAt()))
                .toList();
        String next = more ? encode(shown.get(shown.size() - 1).publishedAt(), shown.get(shown.size() - 1).id()) : null;
        return new CursorPage<>(items, next, more);
    }

    private record Row(UUID id, String body, UUID authorId, UUID groupId, Instant publishedAt) {
    }

    private static final org.springframework.jdbc.core.RowMapper<Row> ROW_MAPPER = (rs, n) -> new Row(
            UUID.fromString(rs.getString("id")),
            rs.getString("body"),
            UUID.fromString(rs.getString("author_id")),
            rs.getObject("group_id") == null ? null : UUID.fromString(rs.getString("group_id")),
            rs.getTimestamp("published_at").toInstant());

    private record Keyset(Instant at, UUID id) {
    }

    private static String encode(Instant at, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((at.toString() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static Keyset decode(String cursor) {
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
