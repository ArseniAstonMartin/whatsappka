package by.whatsappka.identity;

import java.util.UUID;

/** Сессия, для которой подтверждён действующий access JWT. */
public record AuthenticatedSession(UUID userId, UUID sessionId) {
}
