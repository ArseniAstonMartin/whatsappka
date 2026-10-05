package by.whatsappka.media.access;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Аватар и обложка видны любому активному аккаунту. Блокировки добавит задача личных блокировок. */
@Component
public class ProfileMediaResolver implements MediaLinkResolver {

    private final UserAccountRepository users;

    public ProfileMediaResolver(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    public MediaLinkType type() {
        return MediaLinkType.PROFILE_AVATAR;
    }

    @Override
    public boolean canView(UUID viewerId, UUID linkId) {
        return users.findById(viewerId).filter(UserAccount::isActive).isPresent()
                && users.findById(linkId).filter(UserAccount::isActive).isPresent();
    }
}
