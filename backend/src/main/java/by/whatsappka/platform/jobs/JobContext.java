package by.whatsappka.platform.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record JobContext(UUID jobId, JsonNode payload) {
}
