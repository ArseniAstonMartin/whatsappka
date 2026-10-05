package by.whatsappka.search;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Поиск постов (новые первыми, по id при равенстве) и популярных хештегов. Оба — только по видимым зрителю постам. */
@Service
public class ContentSearchQueries {

    /** Сниппет — начало текста; полный текст поиск не отдаёт. */
    static final int SNIPPET_LENGTH = 160;

    public record PostHit(UUID id, UUID authorId, String authorUsername, String authorDisplayName,
                          Instant publishedAt, String snippet) {
    }

    public record HashtagHit(String name, String displayName, long popularity) {
    }

    private final JdbcTemplate jdbc;

    public ContentSearchQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CursorPage<PostHit> posts(UUID viewer, String rawQuery, String cursor, Integer limit) {
        String query = SearchRules.requireQuery(rawQuery);
        int size = SearchRules.pageSize(limit);
        Keyset key = decode(cursor);
        List<Object> params = new ArrayList<>(List.of(viewer, viewer, viewer, viewer,
                SearchRules.pattern(query, false)));
        String sql;
        if (key == null) {
            sql = PostSearchSql.POSTS_FIRST;
        } else {
            sql = PostSearchSql.POSTS_AFTER;
            params.addAll(List.of(key.at().toString(), key.id()));
        }
        params.add(size + 1);
        List<Row> rows = jdbc.query(sql, (rs, n) -> new Row(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("author_id")),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getTimestamp("published_at").toInstant(),
                rs.getString("snippet_source"),
                rs.getInt("body_length")), params.toArray());
        boolean more = rows.size() > size;
        List<Row> shown = more ? rows.subList(0, size) : rows;
        List<PostHit> items = shown.stream()
                .map(r -> new PostHit(r.id(), r.authorId(), r.username(), r.displayName(), r.publishedAt(), snippet(r)))
                .toList();
        String next = more ? encode(shown.get(shown.size() - 1).publishedAt(), shown.get(shown.size() - 1).id()) : null;
        return new CursorPage<>(items, next, more);
    }

    @Transactional(readOnly = true)
    public List<HashtagHit> hashtags(UUID viewer, String rawQuery, Integer limit) {
        String query = SearchRules.requireQuery(SearchRules.hashtagQuery(rawQuery));
        int size = SearchRules.pageSize(limit);
        return jdbc.query(PostSearchSql.HASHTAGS, (rs, n) -> new HashtagHit(
                rs.getString("normalized_name"),
                rs.getString("display_name"),
                rs.getLong("popularity")),
                viewer, viewer, viewer, viewer, SearchRules.pattern(query, false), size);
    }

    private static String snippet(Row row) {
        if (row.snippetSource() == null) {
            return null;
        }
        return row.bodyLength() > SNIPPET_LENGTH
                ? row.snippetSource().substring(0, Math.min(SNIPPET_LENGTH, row.snippetSource().length())) + "…"
                : row.snippetSource();
    }

    private record Row(UUID id, UUID authorId, String username, String displayName, Instant publishedAt,
                       String snippetSource, int bodyLength) {
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
