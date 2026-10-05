package by.whatsappka.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "SessionView")
public record SessionView(
        @Schema(format = "uuid") UUID id,
        @Schema(example = "Safari на iPhone") String deviceLabel,
        Instant createdAt,
        Instant lastUsedAt,
        @Schema(description = "Сессия, из которой выполнен запрос") boolean current
) {
}
