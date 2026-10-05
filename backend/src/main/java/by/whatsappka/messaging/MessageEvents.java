package by.whatsappka.messaging;

import by.whatsappka.platform.outbox.OutboxWriter;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Журнал событий сообщений. Пишется в той же транзакции, что и изменение, вместе с записью outbox. */
@Component
public class MessageEvents {

    private final JdbcTemplate jdbc;
    private final OutboxWriter outbox;

    public MessageEvents(JdbcTemplate jdbc, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    /** type — message.created, message.edited или message.deleted. Номер события берётся из счётчика чата. */
    public void record(UUID conversationId, String type, UUID actorId, UUID messageId) {
        Long eventSeq = jdbc.queryForObject(MessageSql.NEXT_EVENT_SEQ, Long.class, conversationId);
        jdbc.update(MessageSql.INSERT_MESSAGE_EVENT, UUID.randomUUID(), conversationId, eventSeq, type, actorId, messageId);
        outbox.record("conversation", conversationId, type, Map.of(
                "conversationId", conversationId.toString(),
                "messageId", messageId.toString(),
                "eventSeq", eventSeq));
    }
}
