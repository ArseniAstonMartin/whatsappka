package by.whatsappka.platform.idempotency;

import com.fasterxml.jackson.databind.JsonNode;

public record IdempotentResult(int status, JsonNode body, boolean replayed) {
}
