package by.whatsappka.profiles;

import by.whatsappka.identity.account.UserProfile;
import java.util.UUID;

/**
 * Безопасные публичные поля профиля, которые допустимо держать в коротком кэше.
 * Идентификатор, username и решение о видимости не кэшируются — они всегда проверяются на PostgreSQL.
 */
record PublicProfileFields(
        String displayName,
        String bio,
        String statusText,
        boolean verified,
        UUID avatarMediaId,
        UUID coverMediaId
) {

    static PublicProfileFields from(UserProfile profile) {
        return new PublicProfileFields(
                profile.displayName(),
                profile.bio(),
                profile.statusText(),
                profile.isVerified(),
                profile.avatarMediaId(),
                profile.coverMediaId());
    }
}
