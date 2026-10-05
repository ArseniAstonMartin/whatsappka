package by.whatsappka.platform.realtime;

import java.time.Instant;
import java.util.UUID;

/** Оболочка события realtime (PRD §7.2). eventId нужен клиенту, чтобы убрать повторы at-least-once. */
public record RealtimeEvent(
        UUID eventId,
        String type,
        Instant occurredAt,
        UUID entityId,
        long entityVersion,
        UUID conversationId,
        Long eventSeq,
        Object payload
) {
}
