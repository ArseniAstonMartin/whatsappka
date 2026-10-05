package by.whatsappka.notifications;

import by.whatsappka.platform.outbox.OutboxConsumer;
import by.whatsappka.platform.outbox.OutboxEnvelope;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Превращает события подписок и обсуждений в сохранённые уведомления. Ключ события — eventId outbox,
 * поэтому повтор доставки не создаёт второго уведомления и не шлёт второй realtime-сигнал.
 */
@Component
public class NotificationOutboxConsumer implements OutboxConsumer {

    private static final Set<String> EVENTS = Set.of("follow.created", "post.reacted", "comment.reacted", "comment.created");

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final NotificationRealtimePublisher realtime;

    public NotificationOutboxConsumer(JdbcTemplate jdbc, NotificationService notifications,
                                      NotificationRealtimePublisher realtime) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.realtime = realtime;
    }

    @Override
    public String name() {
        return "notifications";
    }

    @Override
    public Set<String> eventTypes() {
        return EVENTS;
    }

    @Override
    public void consume(OutboxEnvelope event) {
        String eventKey = event.eventId().toString();
        for (NotificationPlanner.Planned planned : NotificationPlanner.plan(event.type(), event.payload(), this::postAuthor)) {
            notifications.notify(planned.recipient(), eventKey, planned.type(), planned.actor(), planned.kind(), planned.targetId())
                    .ifPresent((id) -> realtime.published(planned.recipient(), id, planned.type()));
        }
    }

    private UUID postAuthor(UUID postId) {
        return jdbc.query(NotificationSql.POST_AUTHOR, (rs) -> rs.next() ? UUID.fromString(rs.getString(1)) : null, postId);
    }
}
