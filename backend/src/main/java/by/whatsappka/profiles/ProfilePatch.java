package by.whatsappka.profiles;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Изменение собственного профиля. Неизвестные поля (в том числе verified, role) отклоняются, а не игнорируются.
 * Аватар и обложка меняются привязкой медиа, а не этим запросом. Отсутствующее поле (null) не меняется.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "ProfilePatch")
public record ProfilePatch(
        @Size(min = 1, max = 80) String displayName,
        @Size(max = 500) String bio,
        @Size(max = 140) String statusText,
        @Size(min = 1, max = 64) String timezone
) {
}
