package by.whatsappka.communities;

import by.whatsappka.media.access.MediaLinkResolver;
import by.whatsappka.media.access.MediaLinkType;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Обложка сообщества следует тем же правилам видимости, что и аватар. */
@Component
public class CommunityCoverResolver implements MediaLinkResolver {

    private final CommunityAvatarResolver avatars;

    public CommunityCoverResolver(CommunityAvatarResolver avatars) {
        this.avatars = avatars;
    }

    @Override
    public MediaLinkType type() {
        return MediaLinkType.COMMUNITY_COVER;
    }

    @Override
    public boolean canView(UUID viewerId, UUID linkId) {
        return avatars.canView(viewerId, linkId);
    }
}
