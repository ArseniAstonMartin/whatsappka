package by.whatsappka.messaging;

import by.whatsappka.social.SocialRelations;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Общие проверки доступа к диалогу для сигналов realtime: участие и блокировка личного диалога. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConversationGuard {

    private final JdbcTemplate jdbc;
    private final SocialRelations relations;

    public ConversationGuard(JdbcTemplate jdbc, SocialRelations relations) {
        this.jdbc = jdbc;
        this.relations = relations;
    }

    public boolean isActiveMember(UUID conversationId, UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(ConversationSql.IS_ACTIVE_MEMBER, Boolean.class, conversationId, userId));
    }

    /** В личном диалоге блокировка между участниками закрывает и сигналы, как и отправку сообщений. */
    public boolean blockedInDirect(UUID userId, UUID conversationId) {
        List<UUID> counterpart = jdbc.query(MessageSql.DIRECT_COUNTERPART,
                (rs, n) -> UUID.fromString(rs.getString(1)), userId, conversationId);
        return !counterpart.isEmpty() && relations.blockedBetween(userId, counterpart.get(0));
    }
}
