package by.whatsappka.messaging;

import by.whatsappka.platform.realtime.RealtimeEvent;
import by.whatsappka.platform.realtime.RealtimePublisher;
import by.whatsappka.platform.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Правка и удаление сообщений. Всё идёт под блокировкой чата, событие пишется в той же транзакции.
 * Seq сообщения не меняется. Правит только автор и только в течение суток; удаляет автор без срока,
 * модератор чата — чужое только с причиной и записью в аудит.
 *
 * <p>Realtime-рассылку {@code notifyEdited}/{@code notifyDeleted} вызывающий код запускает отдельно,
 * уже после того как {@code edit}/{@code delete} зафиксировали транзакцию — иначе участник получил бы
 * сигнал о правке, которая могла ещё откатиться.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MessageEditService {

    public record Edited(UUID id, long version, Instant updatedAt) {
    }

    private final JdbcTemplate jdbc;
    private final MessageEvents events;
    private final RealtimePublisher realtime;
    private final Clock clock;

    public MessageEditService(JdbcTemplate jdbc, MessageEvents events, RealtimePublisher realtime, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.realtime = realtime;
        this.clock = clock;
    }

    @Transactional
    public Edited edit(UUID actor, UUID conversationId, UUID messageId, String body) {
        lock(conversationId);
        requireActiveMember(conversationId, actor);
        Target target = load(conversationId, messageId);
        if (!target.senderId().equals(actor)) {
            throw ApiException.forbidden();
        }
        if (target.deleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "message_deleted", "Сообщение удалено", List.of(), null);
        }
        Instant now = clock.instant();
        if (!MessageRules.withinEditWindow(target.createdAt(), now)) {
            throw new ApiException(HttpStatus.CONFLICT, "edit_window_closed",
                    "Править сообщение можно в течение суток после отправки", List.of(), null);
        }
        List<UUID> media = jdbc.queryForList(MessageSql.ATTACHMENT_IDS, UUID.class, messageId);
        MessageRules.validate(body, media);
        Edited edited = jdbc.queryForObject(MessageSql.EDIT_TEXT, (rs, n) -> new Edited(
                messageId,
                rs.getLong("version"),
                rs.getTimestamp("updated_at").toInstant()), body, messageId);
        events.record(conversationId, "message.edited", actor, messageId);
        return edited;
    }

    /**
     * Удаление. Текст очищается, вложения скрываются. Чужое сообщение может удалить модератор чата,
     * причина обязательна и попадает в аудит.
     */
    @Transactional
    public Edited delete(UUID actor, UUID conversationId, UUID messageId, String reason) {
        lock(conversationId);
        requireActiveMember(conversationId, actor);
        Target target = load(conversationId, messageId);
        if (target.deleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "already_deleted", "Сообщение уже удалено", List.of(), null);
        }
        boolean own = target.senderId().equals(actor);
        String cleanReason = null;
        if (!own) {
            Boolean moderator = jdbc.queryForObject(MessageSql.IS_CHAT_ADMIN, Boolean.class,
                    conversationId, actor, conversationId, actor);
            if (!Boolean.TRUE.equals(moderator)) {
                throw ApiException.forbidden();
            }
            cleanReason = MessageRules.requireReason(reason);
        }
        Edited deleted = jdbc.queryForObject(MessageSql.DELETE_MESSAGE, (rs, n) -> new Edited(
                messageId, rs.getLong("version"), rs.getTimestamp("updated_at").toInstant()), messageId);
        if (!own) {
            jdbc.update(MessageSql.INSERT_AUDIT, UUID.randomUUID(), messageId, conversationId, actor,
                    "DELETED_BY_ADMIN", cleanReason);
        }
        events.record(conversationId, "message.deleted", actor, messageId);
        return deleted;
    }

    /** Рассылка после коммита: другие открытые диалоги обновляют тот же пузырь по id/version без перезагрузки. */
    @Transactional(readOnly = true)
    public void notifyEdited(UUID conversationId, Edited edited, String body) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", edited.id().toString());
        payload.put("body", body);
        payload.put("version", edited.version());
        payload.put("updatedAt", edited.updatedAt().toString());
        broadcast(conversationId, "message.edited", edited.id(), payload);
    }

    @Transactional(readOnly = true)
    public void notifyDeleted(UUID conversationId, Edited deleted) {
        broadcast(conversationId, "message.deleted", deleted.id(), Map.of(
                "messageId", deleted.id().toString(),
                "version", deleted.version(), "updatedAt", deleted.updatedAt().toString()));
    }

    private void broadcast(UUID conversationId, String type, UUID messageId, Map<String, Object> payload) {
        List<UUID> members = jdbc.queryForList(MessageSql.ACTIVE_MEMBERS, UUID.class, conversationId);
        for (UUID member : members) {
            realtime.deliver(member, new RealtimeEvent(
                    UUID.randomUUID(), type, Instant.now(), messageId, 0L, conversationId, null, payload));
        }
    }

    private void lock(UUID conversationId) {
        jdbc.query(GroupSql.LOCK_CONVERSATION, rs -> { }, conversationId);
    }

    private void requireActiveMember(UUID conversationId, UUID userId) {
        Boolean member = jdbc.queryForObject(ConversationSql.IS_ACTIVE_MEMBER, Boolean.class, conversationId, userId);
        if (!Boolean.TRUE.equals(member)) {
            throw ApiException.notFound();
        }
    }

    private Target load(UUID conversationId, UUID messageId) {
        List<Target> found = jdbc.query(MessageSql.LOAD_FOR_CHANGE, (rs, n) -> new Target(
                UUID.fromString(rs.getString("sender_id")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getObject("deleted_at") != null), messageId, conversationId);
        if (found.isEmpty()) {
            throw ApiException.notFound();
        }
        return found.get(0);
    }

    private record Target(UUID senderId, Instant createdAt, boolean deleted) {
    }
}
