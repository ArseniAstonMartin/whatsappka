package by.whatsappka.messaging.web;

import by.whatsappka.messaging.TypingService;
import by.whatsappka.platform.realtime.RealtimeEvent;
import by.whatsappka.platform.realtime.RealtimePublisher;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/** Команда набора текста: клиент не выбирает адресатов, их вычисляет сервер по участию в диалоге. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TypingStompController {

    private final TypingService typing;
    private final RealtimePublisher realtime;

    public TypingStompController(TypingService typing, RealtimePublisher realtime) {
        this.typing = typing;
        this.realtime = realtime;
    }

    @MessageMapping("/conversations/{id}/typing")
    public void typing(@DestinationVariable("id") UUID conversationId, Principal principal) {
        UUID sender = UUID.fromString(principal.getName());
        Map<String, Object> payload = Map.of("userId", sender.toString(), "expiresInSeconds", TypingService.EXPIRES_IN_SECONDS);
        for (UUID recipient : typing.signal(sender, conversationId)) {
            realtime.deliver(recipient, new RealtimeEvent(UUID.randomUUID(), "typing.changed", Instant.now(),
                    null, 0L, conversationId, null, payload));
        }
    }
}
