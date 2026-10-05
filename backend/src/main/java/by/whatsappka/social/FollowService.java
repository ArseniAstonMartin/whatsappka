package by.whatsappka.social;

import by.whatsappka.platform.outbox.OutboxWriter;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Подписка и отписка. Обе операции идемпотентны: повтор не меняет состояние и не создаёт событие. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FollowService {

    public static final String EVENT_FOLLOW_CREATED = "follow.created";

    private final JdbcTemplate jdbc;
    private final SocialRelations relations;
    private final OutboxWriter outbox;

    public FollowService(JdbcTemplate jdbc, SocialRelations relations, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.relations = relations;
        this.outbox = outbox;
    }

    @Transactional
    public void follow(UUID viewerId, UUID targetId) {
        relations.requireCanFollow(viewerId, targetId);
        int inserted = jdbc.update(SocialSql.INSERT_FOLLOW, viewerId, targetId);
        if (inserted == 1) {
            outbox.record("user", targetId, EVENT_FOLLOW_CREATED, Map.of(
                    "followerId", viewerId.toString(),
                    "followeeId", targetId.toString()));
        }
    }

    @Transactional
    public void unfollow(UUID viewerId, UUID targetId) {
        relations.requireActiveTarget(targetId);
        jdbc.update(SocialSql.DELETE_FOLLOW, viewerId, targetId);
    }
}
