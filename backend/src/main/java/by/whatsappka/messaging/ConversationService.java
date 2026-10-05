package by.whatsappka.messaging;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.social.SocialRelations;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Создание личного диалога. Проверка доступа (активность и блокировки) выполняется до поиска существующего чата,
 * поэтому заблокированный пользователь не получает ни нового, ни существующего диалога.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConversationService {

    public record Created(UUID conversationId, boolean created) {
    }

    private final JdbcTemplate jdbc;
    private final SocialRelations relations;

    public ConversationService(JdbcTemplate jdbc, SocialRelations relations) {
        this.jdbc = jdbc;
        this.relations = relations;
    }

    @Transactional
    public Created openDirect(UUID viewerId, UUID otherId) {
        if (viewerId.equals(otherId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "self_chat", "Нельзя начать диалог с самим собой",
                    List.of(), null);
        }
        relations.requireVisibleTo(viewerId, otherId);
        jdbc.query(ConversationSql.LOCK_PAIR, rs -> { }, viewerId, otherId, viewerId, otherId);
        UUID existing = findDirect(viewerId, otherId);
        if (existing != null) {
            return new Created(existing, false);
        }
        UUID conversationId = UUID.randomUUID();
        jdbc.update(ConversationSql.INSERT_CONVERSATION, conversationId);
        jdbc.update(ConversationSql.INSERT_DIRECT, conversationId, viewerId, otherId, viewerId, otherId);
        jdbc.update(ConversationSql.INSERT_MEMBERSHIP, UUID.randomUUID(), conversationId, viewerId);
        jdbc.update(ConversationSql.INSERT_MEMBERSHIP, UUID.randomUUID(), conversationId, otherId);
        return new Created(conversationId, true);
    }

    private UUID findDirect(UUID a, UUID b) {
        List<UUID> found = jdbc.query(ConversationSql.FIND_DIRECT,
                (rs, row) -> UUID.fromString(rs.getString("conversation_id")), a, b, a, b);
        return found.isEmpty() ? null : found.get(0);
    }
}
