package by.whatsappka.messaging;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
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

/** Список и карточка диалогов для участника. Пагинация по времени последней активности и id. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConversationQueries {

    public record ConversationView(
            UUID id,
            String type,
            Instant lastActivityAt,
            UUID otherId,
            String otherUsername,
            String otherDisplayName,
            boolean blocked,
            LastMessage lastMessage,
            long unread
    ) {
    }

    /** Последнее видимое участнику сообщение; null, если таких сообщений нет. */
    public record LastMessage(long seq, String body, UUID senderId, Instant createdAt) {
    }

    private static final RowMapper<ConversationView> ROW = (rs, n) -> new ConversationView(
            UUID.fromString(rs.getString("id")),
            "DIRECT",
            rs.getTimestamp("updated_at").toInstant(),
            UUID.fromString(rs.getString("other_id")),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getBoolean("blocked"),
            lastMessage(rs),
            rs.getLong("unread"));

    private static LastMessage lastMessage(ResultSet rs) throws SQLException {
        if (rs.getObject("last_seq") == null) {
            return null;
        }
        return new LastMessage(
                rs.getLong("last_seq"),
                rs.getString("last_body"),
                UUID.fromString(rs.getString("last_sender_id")),
                rs.getTimestamp("last_created_at").toInstant());
    }

    private final JdbcTemplate jdbc;

    public ConversationQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CursorPage<ConversationView> list(UUID viewerId, String cursor, int limit) {
        int size = PageSize.limit(limit);
        Keyset key = decode(cursor);
        List<ConversationView> rows = key == null
                ? jdbc.query(ConversationSql.LIST_FIRST, ROW, viewerId, size + 1)
                : jdbc.query(ConversationSql.LIST_AFTER, ROW, viewerId,
                        Timestamp.from(key.at()), key.id(), size + 1);
        boolean more = rows.size() > size;
        List<ConversationView> shown = new ArrayList<>(more ? rows.subList(0, size) : rows);
        String next = null;
        if (more) {
            ConversationView last = shown.get(shown.size() - 1);
            next = encode(last.lastActivityAt(), last.id());
        }
        return new CursorPage<>(shown, next, more);
    }

    @Transactional(readOnly = true)
    public ConversationView get(UUID viewerId, UUID conversationId) {
        List<ConversationView> found = jdbc.query(ConversationSql.GET_FOR_MEMBER, ROW, viewerId, conversationId);
        if (found.isEmpty()) {
            throw ApiException.notFound();
        }
        return found.get(0);
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
