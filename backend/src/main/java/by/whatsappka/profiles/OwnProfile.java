package by.whatsappka.profiles;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Собственный профиль для экрана настроек: дополнительно email и часовой пояс. Роли здесь не передаются. */
@Schema(name = "OwnProfile")
public record OwnProfile(
        UUID id,
        String username,
        String email,
        String displayName,
        String bio,
        String statusText,
        String timezone,
        boolean verified,
        UUID avatarMediaId,
        UUID coverMediaId
) {
}
