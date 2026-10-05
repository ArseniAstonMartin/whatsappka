package by.whatsappka.messaging;

import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.social.SocialRelations;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сохранение сообщения. Чат блокируется, затем проверяются членство, блокировки и вложения, выделяется seq
 * и пишется сообщение с событием outbox — в одной транзакции. Вызывающий код получает подтверждение только после commit.
 */
@Service
public class MessageService {

    public record Sent(UUID id, long seq, boolean replayed, Instant createdAt) {
    }

    private static final List<String> ATTACHABLE = List.of("CHAT_IMAGE", "CHAT_DOCUMENT");

    private final JdbcTemplate jdbc;
    private final SocialRelations relations;
    private final OutboxWriter outbox;

    public MessageService(JdbcTemplate jdbc, SocialRelations relations, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.relations = relations;
        this.outbox = outbox;
    }

    @Transactional
    public Sent send(UUID sender, UUID conversationId, UUID clientMessageId, String body, List<UUID> media) {
        MessageRules.validate(body, media);
        jdbc.query(GroupSql.LOCK_CONVERSATION, rs -> { }, conversationId);
        Boolean member = jdbc.queryForObject(ConversationSql.IS_ACTIVE_MEMBER, Boolean.class, conversationId, sender);
        if (!Boolean.TRUE.equals(member)) {
            throw ApiException.notFound();
        }
        requireNotBlockedInDirect(sender, conversationId);

        String fingerprint = MessageRules.fingerprint(body, media);
        List<Stored> existing = jdbc.query(MessageSql.FIND_BY_CLIENT_ID,
                (rs, n) -> new Stored(
                        UUID.fromString(rs.getString("id")),
                        rs.getLong("seq"),
                        rs.getString("fingerprint"),
                        rs.getTimestamp("created_at").toInstant()),
                conversationId, sender, clientMessageId);
        if (!existing.isEmpty()) {
            Stored stored = existing.get(0);
            MessageRules.requireSameContent(stored.fingerprint(), fingerprint);
            return new Sent(stored.id(), stored.seq(), true, stored.createdAt());
        }

        for (UUID mediaId : media) {
            requireAttachable(sender, mediaId);
        }
        Long seq = jdbc.queryForObject(MessageSql.NEXT_SEQ, Long.class, conversationId);
        UUID id = UUID.randomUUID();
        jdbc.update(MessageSql.INSERT_MESSAGE, id, conversationId, sender, seq, clientMessageId, body, fingerprint);
        for (int i = 0; i < media.size(); i++) {
            jdbc.update(MessageSql.INSERT_ATTACHMENT, id, media.get(i), i + 1);
        }
        outbox.record("conversation", conversationId, "message.created", Map.of(
                "messageId", id.toString(),
                "conversationId", conversationId.toString(),
                "seq", seq,
                "senderId", sender.toString()));
        return new Sent(id, seq, false, Instant.now());
    }

    /** Получатели нового сообщения: действующие участники, кроме отправителя. */
    @Transactional(readOnly = true)
    public List<UUID> recipients(UUID conversationId, UUID sender) {
        return jdbc.queryForList(MessageSql.ACTIVE_MEMBERS, UUID.class, conversationId).stream()
                .filter((id) -> !id.equals(sender))
                .toList();
    }

    /** В личном диалоге блокировка запрещает новые сообщения в обе стороны. */
    private void requireNotBlockedInDirect(UUID sender, UUID conversationId) {
        List<UUID> counterpart = jdbc.query(MessageSql.DIRECT_COUNTERPART,
                (rs, n) -> UUID.fromString(rs.getString(1)), sender, conversationId);
        if (!counterpart.isEmpty() && relations.blockedBetween(sender, counterpart.get(0))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "interaction_blocked",
                    "Отправка недоступна: между вами блокировка", List.of(), null);
        }
    }

    /** Вложение: готовый собственный файл назначения для чата, ещё не прикреплённый к другому сообщению. */
    private void requireAttachable(UUID sender, UUID mediaId) {
        List<Row> rows = jdbc.query(MessageSql.MEDIA_FOR_ATTACHMENT, (rs, n) -> new Row(
                UUID.fromString(rs.getString("owner_id")),
                rs.getString("status"),
                rs.getString("purpose"),
                rs.getObject("deleted_at") != null,
                rs.getLong("attached")), mediaId);
        if (rows.isEmpty() || rows.get(0).deleted()) {
            throw ApiException.notFound();
        }
        Row row = rows.get(0);
        if (!row.owner().equals(sender)) {
            throw ApiException.notFound();
        }
        if (!"READY".equals(row.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "media_not_ready", "Вложение ещё не готово", List.of(), null);
        }
        if (!ATTACHABLE.contains(row.purpose())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "purpose_mismatch",
                    "Файл не подходит как вложение чата", List.of(), null);
        }
        if (row.attached() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "attachment_in_use",
                    "Вложение уже прикреплено к другому сообщению", List.of(), null);
        }
    }

    private record Stored(UUID id, long seq, String fingerprint, Instant createdAt) {
    }

    private record Row(UUID owner, String status, String purpose, boolean deleted, long attached) {
    }
}
