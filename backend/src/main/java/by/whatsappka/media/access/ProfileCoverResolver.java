package by.whatsappka.media.access;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Обложка профиля следует тем же правилам, что и аватар. */
@Component
public class ProfileCoverResolver implements MediaLinkResolver {

    private final ProfileMediaResolver avatars;

    public ProfileCoverResolver(ProfileMediaResolver avatars) {
        this.avatars = avatars;
    }

    @Override
    public MediaLinkType type() {
        return MediaLinkType.PROFILE_COVER;
    }

    @Override
    public boolean canView(UUID viewerId, UUID linkId) {
        return avatars.canView(viewerId, linkId);
    }
}
