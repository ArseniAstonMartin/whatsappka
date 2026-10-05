package by.whatsappka.communities;

import by.whatsappka.media.access.MediaLinkResolver;
import by.whatsappka.media.access.MediaLinkType;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Аватар сообщества виден так же, как сама группа: публично или только участникам и владельцу. */
@Component
public class CommunityAvatarResolver implements MediaLinkResolver {

    private final JdbcTemplate jdbc;

    public CommunityAvatarResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MediaLinkType type() {
        return MediaLinkType.COMMUNITY_AVATAR;
    }

    @Override
    public boolean canView(UUID viewerId, UUID linkId) {
        List<CommunityRow> rows = jdbc.query(CommunitySql.ROW_BY_ID, CommunityRow.MAPPER, linkId);
        if (rows.isEmpty()) {
            return false;
        }
        CommunityRow group = rows.get(0);
        if (group.isDeleted() || group.isHidden()) {
            return false;
        }
        if (group.isPublic()) {
            return true;
        }
        if (group.ownerId().equals(viewerId)) {
            return true;
        }
        List<String> roles = jdbc.queryForList(CommunitySql.ROLE_OF, String.class, group.id(), viewerId);
        return !roles.isEmpty();
    }
}
