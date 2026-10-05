package by.whatsappka.messaging.web;

import by.whatsappka.platform.ratelimit.RateLimiter;
import by.whatsappka.platform.ratelimit.RateLimitPolicy;
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

    private final RateLimiter limits;

    public MessageStompController(MessageService messages, RealtimePublisher realtime, RateLimiter limits) {
        this.limits = limits;
        this.messages = messages;
        this.realtime = realtime;
    }

    @MessageMapping("/conversations/{id}/messages")
    public void send(@DestinationVariable("id") UUID conversationId, @Payload SendPayload payload, Principal principal) {
        UUID sender = UUID.fromString(principal.getName());
        UUID clientMessageId = payload.clientMessageId();
        try {
            // PRD: не более 30 сообщений в минуту на пользователя.
            limits.consume("message", sender.toString(), 30, RateLimitPolicy.MINUTE);
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
            Map<String, Object> details = new java.util.HashMap<>();
            details.put("clientMessageId", String.valueOf(clientMessageId));
            details.put("code", failure.code());
            details.put("detail", failure.detail());
            if (failure.retryAfter() != null) {
                details.put("retryAfterSeconds", failure.retryAfter().toSeconds());
            }
            realtime.deliver(sender, event("message.failed", conversationId, null, null, details));
        }
    }

    private static RealtimeEvent event(String type, UUID conversationId, UUID messageId, Long seq, Object payload) {
        return new RealtimeEvent(UUID.randomUUID(), type, Instant.now(), messageId, 0L, conversationId, seq, payload);
    }

    public record SendPayload(UUID clientMessageId, String body, List<UUID> attachments) {
    }
}
