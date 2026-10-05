package by.whatsappka.social;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Списки связей и счётчики. Счётчики считаются из таблицы связей, а не хранятся отдельно. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FollowQueries {

    public record UserSummary(UUID id, String username, String displayName) {
    }

    public record Relations(long followers, long following, boolean followedByViewer, boolean self) {
    }

    private final JdbcTemplate jdbc;
    private final SocialRelations relations;

    public FollowQueries(JdbcTemplate jdbc, SocialRelations relations) {
        this.jdbc = jdbc;
        this.relations = relations;
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> followers(UUID targetId, String cursor, int limit) {
        relations.requireActiveTarget(targetId);
        return page(cursor, limit, (Keyset key, int size) -> key == null
                ? jdbc.query(SocialSql.FOLLOWERS_FIRST, ROW, targetId, size + 1)
                : jdbc.query(SocialSql.FOLLOWERS_AFTER, ROW, targetId, Timestamp.from(key.at()), key.id(), size + 1));
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> following(UUID targetId, String cursor, int limit) {
        relations.requireActiveTarget(targetId);
        return page(cursor, limit, (Keyset key, int size) -> key == null
                ? jdbc.query(SocialSql.FOLLOWING_FIRST, ROW, targetId, size + 1)
                : jdbc.query(SocialSql.FOLLOWING_AFTER, ROW, targetId, Timestamp.from(key.at()), key.id(), size + 1));
    }

    @Transactional(readOnly = true)
    public Relations relations(UUID viewerId, UUID targetId) {
        relations.requireActiveTarget(targetId);
        long followers = jdbc.queryForObject(SocialSql.COUNT_FOLLOWERS, Long.class, targetId);
        long following = jdbc.queryForObject(SocialSql.COUNT_FOLLOWING, Long.class, targetId);
        Boolean followed = jdbc.queryForObject(SocialSql.IS_FOLLOWING, Boolean.class, viewerId, targetId);
        return new Relations(followers, following, Boolean.TRUE.equals(followed), viewerId.equals(targetId));
    }

    private interface PageLoader {
        List<Row> load(Keyset key, int size);
    }

    private record Row(UUID id, String username, String displayName, Instant at) {
    }

    private record Keyset(Instant at, UUID id) {
    }

    private static final org.springframework.jdbc.core.RowMapper<Row> ROW = (rs, n) -> new Row(
            UUID.fromString(rs.getString("id")),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getTimestamp("created_at").toInstant());

    private static CursorPage<UserSummary> page(String cursor, int limit, PageLoader loader) {
        int size = by.whatsappka.platform.web.PageSize.limit(limit);
        Keyset key = decode(cursor);
        List<Row> rows = loader.load(key, size);
        boolean more = rows.size() > size;
        List<Row> shown = more ? rows.subList(0, size) : rows;
        List<UserSummary> items = shown.stream()
                .map((row) -> new UserSummary(row.id(), row.username(), row.displayName()))
                .toList();
        String next = null;
        if (more) {
            Row last = shown.get(shown.size() - 1);
            next = encode(last.at(), last.id());
        }
        return new CursorPage<>(items, next, more);
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
