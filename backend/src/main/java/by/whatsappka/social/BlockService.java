package by.whatsappka.social;

import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Блокировка и разблокировка. Блокировка и удаление подписок идут одной транзакцией, событие уходит через outbox
 * для realtime-модулей. История уже существующих личных сообщений не удаляется.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BlockService {

    public static final String EVENT_BLOCKED = "user.blocked";

    private final JdbcTemplate jdbc;
    private final SocialRelations relations;
    private final OutboxWriter outbox;

    public BlockService(JdbcTemplate jdbc, SocialRelations relations, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.relations = relations;
        this.outbox = outbox;
    }

    @Transactional
    public void block(UUID viewerId, UUID targetId) {
        if (viewerId.equals(targetId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "self_block", "Нельзя заблокировать себя",
                    List.of(), null);
        }
        relations.requireActiveTarget(targetId);
        int inserted = jdbc.update(SocialSql.INSERT_BLOCK, viewerId, targetId);
        if (inserted == 1) {
            jdbc.update(SocialSql.DELETE_FOLLOWS_BETWEEN, viewerId, targetId, targetId, viewerId);
            outbox.record("user", targetId, EVENT_BLOCKED, Map.of(
                    "blockerId", viewerId.toString(),
                    "blockedId", targetId.toString()));
        }
    }

    @Transactional
    public void unblock(UUID viewerId, UUID targetId) {
        // Разблокировка не восстанавливает подписки и не пишет событие: связи создаются заново обычными действиями.
        jdbc.update(SocialSql.DELETE_BLOCK, viewerId, targetId);
    }
}
