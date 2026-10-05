package by.whatsappka.platform.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "ApiError")
public record ApiError(
        @Schema(example = "400") int status,
        @Schema(example = "validation_failed") String code,
        @Schema(example = "Проверьте поля запроса") String detail,
        List<FieldErrorDetail> fieldErrors,
        @Schema(format = "uuid", example = "3f1c2c3e-7c1a-4d2b-9a6e-1b8e5d0c4a11") String traceId
) {
    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
