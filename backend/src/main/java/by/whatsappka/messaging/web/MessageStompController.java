package by.whatsappka.messaging.web;

import by.whatsappka.messaging.MessageService;
import by.whatsappka.platform.realtime.RealtimeEvent;
import by.whatsappka.platform.realtime.RealtimePublisher;
import by.whatsappka.platform.web.ApiException;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * Отправка сообщения по STOMP. Подтверждение отправителю и доставка получателям идут только после commit,
 * сбой отправки возвращается отправителю с кодом, а не теряется.
 */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MessageStompController {

    private final MessageService messages;
    private final RealtimePublisher realtime;

    public MessageStompController(MessageService messages, RealtimePublisher realtime) {
        this.messages = messages;
        this.realtime = realtime;
    }

    @MessageMapping("/conversations/{id}/messages")
    public void send(@DestinationVariable("id") UUID conversationId, @Payload SendPayload payload, Principal principal) {
        UUID sender = UUID.fromString(principal.getName());
        UUID clientMessageId = payload.clientMessageId();
        try {
            MessageService.Sent sent = messages.send(sender, conversationId, clientMessageId, payload.body(),
                    payload.attachments() == null ? List.of() : payload.attachments());
            Map<String, Object> body = Map.of(
                    "clientMessageId", String.valueOf(clientMessageId),
                    "messageId", sent.id().toString(),
                    "seq", sent.seq(),
                    "replayed", sent.replayed());
            realtime.deliver(sender, event("message.saved", conversationId, sent.id(), sent.seq(), body));
            if (!sent.replayed()) {
                for (UUID recipient : messages.recipients(conversationId, sender)) {
                    realtime.deliver(recipient, event("message.created", conversationId, sent.id(), sent.seq(), body));
                }
            }
        } catch (ApiException failure) {
            realtime.deliver(sender, event("message.failed", conversationId, null, null, Map.of(
                    "clientMessageId", String.valueOf(clientMessageId),
                    "code", failure.code(),
                    "detail", failure.detail())));
        }
    }

    private static RealtimeEvent event(String type, UUID conversationId, UUID messageId, Long seq, Object payload) {
        return new RealtimeEvent(UUID.randomUUID(), type, Instant.now(), messageId, 0L, conversationId, seq, payload);
    }

    public record SendPayload(UUID clientMessageId, String body, List<UUID> attachments) {
    }
}
