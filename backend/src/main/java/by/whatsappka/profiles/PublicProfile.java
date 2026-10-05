package by.whatsappka.profiles;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Публичный профиль другого пользователя. Здесь нет email, ролей, часового пояса и служебных полей.
 */
@Schema(name = "PublicProfile")
public record PublicProfile(
        UUID id,
        String username,
        String displayName,
        String bio,
        String statusText,
        boolean verified,
        UUID avatarMediaId,
        UUID coverMediaId
) {
}
