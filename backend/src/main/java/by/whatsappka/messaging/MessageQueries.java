package by.whatsappka.messaging;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * История сообщений. Курсор — seq последнего показанного сообщения; страница идёт от новых к старым.
 * Удалённое сообщение остаётся в истории пустой заглушкой, без текста и вложений.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MessageQueries {

    public record Attachment(UUID mediaId, int position, String purpose) {
    }

    public record MessageView(
            UUID id,
            long seq,
            UUID senderId,
            String body,
            Instant createdAt,
            Instant updatedAt,
            long version,
            boolean deleted,
            List<Attachment> attachments
    ) {
    }

    private final JdbcTemplate jdbc;

    public MessageQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CursorPage<MessageView> history(UUID viewerId, UUID conversationId, String cursor, Integer limit) {
        int size = PageSize.limit(limit);
        List<Long> joined = jdbc.query(MessageSql.ACTIVE_JOINED_SEQ,
                (rs, n) -> rs.getLong(1), conversationId, viewerId);
        if (joined.isEmpty()) {
            throw ApiException.notFound();
        }
        Long before = parseCursor(cursor);
        List<Row> rows = before == null
                ? jdbc.query(MessageSql.HISTORY_FIRST, ROW, conversationId, joined.get(0), size + 1)
                : jdbc.query(MessageSql.HISTORY_BEFORE, ROW, conversationId, joined.get(0), before, size + 1);
        boolean more = rows.size() > size;
        List<Row> shown = more ? rows.subList(0, size) : rows;
        Map<UUID, List<Attachment>> attachments = attachmentsFor(shown);
        List<MessageView> items = new ArrayList<>(shown.size());
        for (Row row : shown) {
            boolean deleted = row.deletedAt() != null;
            items.add(new MessageView(
                    row.id(),
                    row.seq(),
                    row.senderId(),
                    deleted ? null : row.body(),
                    row.createdAt(),
                    row.updatedAt(),
                    row.version(),
                    deleted,
                    deleted ? List.of() : attachments.getOrDefault(row.id(), List.of())));
        }
        String next = more ? String.valueOf(shown.get(shown.size() - 1).seq()) : null;
        return new CursorPage<>(items, next, more);
    }

    private Map<UUID, List<Attachment>> attachmentsFor(List<Row> rows) {
        List<UUID> ids = rows.stream().filter(row -> row.deletedAt() == null).map(Row::id).toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        Map<UUID, List<Attachment>> byMessage = new HashMap<>();
        jdbc.query(MessageSql.ATTACHMENTS_PREFIX + placeholders + MessageSql.ATTACHMENTS_SUFFIX, rs -> {
            UUID messageId = UUID.fromString(rs.getString("message_id"));
            byMessage.computeIfAbsent(messageId, k -> new ArrayList<>()).add(new Attachment(
                    UUID.fromString(rs.getString("media_id")),
                    rs.getInt("position"),
                    rs.getString("purpose")));
        }, ids.toArray());
        return byMessage;
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            long seq = Long.parseLong(cursor.trim());
            if (seq < 1) {
                throw new NumberFormatException(cursor);
            }
            return seq;
        } catch (NumberFormatException malformed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_cursor", "Курсор страницы некорректен",
                    List.of(), null);
        }
    }

    private record Row(UUID id, long seq, UUID senderId, String body, Instant createdAt,
                       Instant updatedAt, long version, Instant deletedAt) {
    }

    private static final RowMapper<Row> ROW = (rs, n) -> new Row(
            UUID.fromString(rs.getString("id")),
            rs.getLong("seq"),
            UUID.fromString(rs.getString("sender_id")),
            rs.getString("body"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version"),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant());
}
