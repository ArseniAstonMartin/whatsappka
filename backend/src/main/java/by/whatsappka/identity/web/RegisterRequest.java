package by.whatsappka.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest")
public record RegisterRequest(
        @Schema(example = "anna@example.by")
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @Schema(example = "anna_k")
        @NotBlank
        @Size(min = 3, max = 30)
        @Pattern(regexp = "[A-Za-z0-9_]+")
        String username,

        @Schema(example = "correct-horse-battery")
        @NotNull
        @Size(min = 10, max = 72)
        String password
) {
}
