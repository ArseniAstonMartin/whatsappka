package by.whatsappka.messaging.web;

import by.whatsappka.messaging.MessageReadService;
import by.whatsappka.platform.realtime.RealtimePublisher;
import by.whatsappka.platform.web.ApiException;
import java.security.Principal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/** Прочтение по STOMP: то же правило, что и REST. Ошибка уходит отправителю событием, а не теряется. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ReadStompController {

    private final MessageReadService reads;
    private final RealtimePublisher realtime;

    public ReadStompController(MessageReadService reads, RealtimePublisher realtime) {
        this.reads = reads;
        this.realtime = realtime;
    }

    @MessageMapping("/conversations/{id}/read")
    public void read(@DestinationVariable("id") UUID conversationId, @Payload ReadPayload payload, Principal principal) {
        UUID viewer = UUID.fromString(principal.getName());
        try {
            MessageReadService.ReadState state = reads.markRead(viewer, conversationId, payload.seq());
            realtime.deliver(viewer, ReadEvents.of(conversationId, state));
        } catch (ApiException failure) {
            realtime.deliver(viewer, ReadEvents.failed(conversationId, failure.code()));
        }
    }

    public record ReadPayload(long seq) {
    }
}
