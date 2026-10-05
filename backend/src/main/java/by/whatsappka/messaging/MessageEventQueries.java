package by.whatsappka.messaging;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.PageSize;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Журнал событий чата для синхронизации клиента. Курсор — номер последнего события, которое клиент применил.
 * fullSyncRequired означает, что инкрементальная синхронизация невозможна: курсор старше удалённого хвоста
 * журнала или впереди счётчика. Тогда клиент перечитывает чат целиком.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MessageEventQueries {

    public record EventView(
            long eventSeq,
            String type,
            Instant occurredAt,
            UUID actorId,
            UUID messageId,
            Long messageSeq
    ) {
    }

    public record EventsPage(List<EventView> items, Long nextCursor, boolean hasMore, boolean fullSyncRequired) {
        public EventsPage {
            items = List.copyOf(items);
        }
    }

    private final JdbcTemplate jdbc;

    public MessageEventQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public EventsPage events(UUID viewerId, UUID conversationId, Long cursor, Integer limit) {
        int size = PageSize.limit(limit);
        List<Long> joined = jdbc.query(MessageSql.ACTIVE_JOINED_SEQ, (rs, n) -> rs.getLong(1), conversationId, viewerId);
        if (joined.isEmpty()) {
            throw ApiException.notFound();
        }
        List<long[]> state = jdbc.query(MessageSql.EVENT_CURSOR_STATE, (rs, n) -> new long[] {
                rs.getLong("events_floor"), rs.getLong("next_event_seq")}, conversationId);
        long floor = state.get(0)[0];
        long next = state.get(0)[1];
        if (cursor != null && (cursor < floor || cursor > next)) {
            return new EventsPage(List.of(), null, false, true);
        }
        long after = cursor == null ? floor : cursor;
        List<EventView> rows = jdbc.query(MessageSql.EVENTS_AFTER, (rs, n) -> new EventView(
                rs.getLong("event_seq"),
                rs.getString("type"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_id") == null ? null : UUID.fromString(rs.getString("actor_id")),
                rs.getObject("message_id") == null ? null : UUID.fromString(rs.getString("message_id")),
                rs.getObject("message_seq") == null ? null : rs.getLong("message_seq")),
                conversationId, after, joined.get(0), size + 1);
        boolean more = rows.size() > size;
        List<EventView> shown = new ArrayList<>(more ? rows.subList(0, size) : rows);
        Long last = shown.isEmpty() ? cursor : shown.get(shown.size() - 1).eventSeq();
        return new EventsPage(shown, last, more, false);
    }
}
