package by.whatsappka.content;

import by.whatsappka.platform.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Общий маппер строки и keyset-курсор ({@code published_at, id}) для публичных списков постов —
 * ленты, профиля, группы и хештега (TASK-044, TASK-045).
 */
final class PostListingSupport {

    private PostListingSupport() {
    }

    static final RowMapper<PostSummaryPublic> ROW_MAPPER = (rs, n) -> new PostSummaryPublic(
            UUID.fromString(rs.getString("id")),
            rs.getString("body"),
            UUID.fromString(rs.getString("author_id")),
            rs.getObject("group_id") == null ? null : UUID.fromString(rs.getString("group_id")),
            rs.getTimestamp("published_at").toInstant());

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
