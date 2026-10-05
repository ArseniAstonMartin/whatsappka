package by.whatsappka.messaging.web;

import by.whatsappka.messaging.MessageReadService;
import by.whatsappka.platform.realtime.RealtimeEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Событие прочтения для устройств самого пользователя. В журнал чата не пишется: чужие устройства его не видят. */
final class ReadEvents {

    private ReadEvents() {
    }

    static RealtimeEvent of(UUID conversationId, MessageReadService.ReadState state) {
        return new RealtimeEvent(UUID.randomUUID(), "conversation.read", Instant.now(), conversationId, 0L,
                conversationId, null, Map.of("lastReadSeq", state.lastReadSeq(), "unread", state.unread()));
    }

    static RealtimeEvent failed(UUID conversationId, String code) {
        return new RealtimeEvent(UUID.randomUUID(), "conversation.read.failed", Instant.now(), conversationId, 0L,
                conversationId, null, Map.of("code", code));
    }
}
