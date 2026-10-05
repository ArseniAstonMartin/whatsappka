package by.whatsappka.notifications;

import by.whatsappka.platform.outbox.OutboxConsumer;
import by.whatsappka.platform.outbox.OutboxEnvelope;
import java.util.List;
import java.util.Optional;
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

    private static final Set<String> EVENTS = Set.of(
            "follow.created", "post.reacted", "comment.reacted", "comment.created",
            "message.created", "conversation.invitation.created", "group.invitation_created",
            "group.join_request_created", "group.join_request_accepted", "group.join_request_rejected");

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
        for (NotificationPlanner.Planned planned
                : NotificationPlanner.plan(event.type(), event.payload(), new SqlLookups())) {
            notifications.notify(planned.recipient(), eventKey, planned.type(), planned.actor(), planned.kind(),
                            planned.targetId(), planned.messageSeq())
                    .ifPresent((id) -> realtime.published(planned.recipient(), id, planned.type(), planned.targetId()));
        }
    }

    /** Данные для планировщика из базы. Каждый запрос отвечает только на то, что нужно событию. */
    private final class SqlLookups implements NotificationPlanner.Lookups {

        @Override
        public UUID postAuthor(UUID postId) {
            return jdbc.query(NotificationSql.POST_AUTHOR, (rs) -> rs.next() ? UUID.fromString(rs.getString(1)) : null, postId);
        }

        @Override
        public Optional<UUID> chatInviter(UUID invitationId) {
            return uuidOf(NotificationSql.CHAT_INVITER, invitationId);
        }

        @Override
        public Optional<UUID> communityInviter(UUID invitationId) {
            return uuidOf(NotificationSql.COMMUNITY_INVITER, invitationId);
        }

        @Override
        public List<UUID> communityAdmins(UUID groupId) {
            return jdbc.queryForList(NotificationSql.COMMUNITY_ADMINS, UUID.class, groupId, groupId);
        }

        @Override
        public Optional<NotificationPlanner.MessageRef> message(UUID messageId) {
            return jdbc.query(NotificationSql.MESSAGE_REF, (rs) -> rs.next()
                    ? Optional.of(new NotificationPlanner.MessageRef(
                            UUID.fromString(rs.getString("sender_id")), rs.getLong("seq"),
                            UUID.fromString(rs.getString("conversation_id"))))
                    : Optional.empty(), messageId);
        }

        @Override
        public List<UUID> messageRecipients(UUID conversationId, long seq, UUID sender) {
            return jdbc.queryForList(NotificationSql.MESSAGE_RECIPIENTS, UUID.class, conversationId, sender, seq);
        }

        private Optional<UUID> uuidOf(String sql, UUID id) {
            return jdbc.query(sql, (rs) -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(), id);
        }
    }
}
