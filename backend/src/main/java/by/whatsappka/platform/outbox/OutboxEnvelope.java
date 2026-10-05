package by.whatsappka.platform.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Общий конверт события. eventId стабилен между повторами: consumer по нему отсекает дубли.
 */
public record OutboxEnvelope(
        UUID eventId,
        String type,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        JsonNode payload
) {
}
