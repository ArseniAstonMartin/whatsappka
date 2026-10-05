package by.whatsappka.messaging;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.PageSize;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

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

    public record EventsPage(List<EventView> items, Long nextCursor, boolean hasMore, boolean fullSyncRequired,
                             UUID membershipId, List<MessageQueries.MessageView> messages) {
        public EventsPage {
            items = List.copyOf(items);
            messages = List.copyOf(messages);
        }
    }

    public record Head(long cursor, UUID membershipId) {
    }

    private record Membership(UUID id, long joinedSeq) {
    }

    /**
     * Текущая граница журнала. Клиент берёт её до загрузки истории: события после неё он догонит повторно,
     * а повтор уже применённого отбрасывается по ID/version, поэтому пропуска не будет.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Head head(UUID viewerId, UUID conversationId) {
        Membership member = membership(viewerId, conversationId);
        List<Long> next = jdbc.query(MessageSql.EVENT_CURSOR_STATE, (rs, n) -> rs.getLong("next_event_seq"), conversationId);
        return new Head(next.get(0), member.id());
    }

    private final JdbcTemplate jdbc;
    private final MessageQueries messages;

    public MessageEventQueries(JdbcTemplate jdbc, MessageQueries messages) {
        this.jdbc = jdbc;
        this.messages = messages;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EventsPage events(UUID viewerId, UUID conversationId, Long cursor, Integer limit) {
        int size = PageSize.limit(limit);
        Membership member = membership(viewerId, conversationId);
        List<long[]> state = jdbc.query(MessageSql.EVENT_CURSOR_STATE, (rs, n) -> new long[] {
                rs.getLong("events_floor"), rs.getLong("next_event_seq")}, conversationId);
        long floor = state.get(0)[0];
        long next = state.get(0)[1];
        if (cursor != null && (cursor < floor || cursor > next)) {
            return new EventsPage(List.of(), null, false, true, member.id(), List.of());
        }
        long after = cursor == null ? floor : cursor;
        List<EventView> rows = jdbc.query(MessageSql.EVENTS_AFTER, (rs, n) -> new EventView(
                rs.getLong("event_seq"),
                rs.getString("type"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_id") == null ? null : UUID.fromString(rs.getString("actor_id")),
                rs.getObject("message_id") == null ? null : UUID.fromString(rs.getString("message_id")),
                rs.getObject("message_seq") == null ? null : rs.getLong("message_seq")),
                conversationId, after, member.joinedSeq(), size + 1);
        boolean more = rows.size() > size;
        List<EventView> shown = new ArrayList<>(more ? rows.subList(0, size) : rows);
        Long last = more ? shown.get(shown.size() - 1).eventSeq() : next;
        List<UUID> ids = shown.stream().map(EventView::messageId).filter(java.util.Objects::nonNull).distinct().toList();
        return new EventsPage(shown, last, more, false, member.id(),
                messages.snapshots(viewerId, conversationId, member.joinedSeq(), ids));
    }

    private Membership membership(UUID viewerId, UUID conversationId) {
        List<Membership> rows = jdbc.query(MessageSql.ACTIVE_JOINED_SEQ,
                (rs, n) -> new Membership(rs.getObject("id", UUID.class), rs.getLong("joined_seq")),
                conversationId, viewerId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        return rows.get(0);
    }
}
