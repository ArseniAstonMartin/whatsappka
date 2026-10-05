package by.whatsappka.platform.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FieldError")
public record FieldErrorDetail(
        @Schema(example = "email") String field,
        @Schema(example = "Обязательное поле") String message
) {
}
