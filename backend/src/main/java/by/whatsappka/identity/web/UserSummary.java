package by.whatsappka.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "UserSummary")
public record UserSummary(
        @Schema(format = "uuid", example = "3f1c2c3e-7c1a-4d2b-9a6e-1b8e5d0c4a11") UUID id,
        @Schema(example = "anna_k") String username
) {
}
