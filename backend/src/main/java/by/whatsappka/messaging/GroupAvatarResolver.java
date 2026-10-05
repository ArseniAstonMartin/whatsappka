package by.whatsappka.messaging;

import by.whatsappka.media.access.MediaLinkResolver;
import by.whatsappka.media.access.MediaLinkType;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Аватар группы виден только её действующим участникам. */
@Component
@ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class GroupAvatarResolver implements MediaLinkResolver {

    private final JdbcTemplate jdbc;

    public GroupAvatarResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MediaLinkType type() {
        return MediaLinkType.GROUP_AVATAR;
    }

    @Override
    public boolean canView(UUID viewerId, UUID conversationId) {
        Boolean member = jdbc.queryForObject(ConversationSql.IS_ACTIVE_MEMBER, Boolean.class, conversationId, viewerId);
        return Boolean.TRUE.equals(member);
    }
}
