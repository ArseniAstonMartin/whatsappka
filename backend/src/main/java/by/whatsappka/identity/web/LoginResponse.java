package by.whatsappka.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginResponse")
public record LoginResponse(
        @Schema(description = "Access JWT хранится в памяти SPA") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900") long expiresIn,
        UserSummary user
) {
}
