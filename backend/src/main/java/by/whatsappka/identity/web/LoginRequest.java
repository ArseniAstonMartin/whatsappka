package by.whatsappka.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest")
public record LoginRequest(
        @Schema(example = "anna@example.by")
        @NotBlank
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(max = 72)
        String password
) {
}
