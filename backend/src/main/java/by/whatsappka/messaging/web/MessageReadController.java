package by.whatsappka.messaging.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.messaging.MessageReadService;
import by.whatsappka.platform.realtime.RealtimePublisher;
import by.whatsappka.platform.web.ApiV1Controller;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MessageReadController {

    private final MessageReadService reads;
    private final RealtimePublisher realtime;

    public MessageReadController(MessageReadService reads, RealtimePublisher realtime) {
        this.reads = reads;
        this.realtime = realtime;
    }

    /** Отметить прочитанным до seq включительно. Прогресс только растёт; ответ — новое состояние. */
    @PostMapping("/conversations/{id}/read")
    public MessageReadService.ReadState markRead(
            @PathVariable("id") UUID conversationId,
            @RequestBody ReadRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        MessageReadService.ReadState state = reads.markRead(viewer.userId(), conversationId, request.seq());
        realtime.deliver(viewer.userId(), ReadEvents.of(conversationId, state));
        return state;
    }

    @GetMapping("/conversations/{id}/unread")
    public MessageReadService.ReadState unread(
            @PathVariable("id") UUID conversationId,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return reads.unread(viewer.userId(), conversationId);
    }

    /** Сколько участников, имевших доступ к сообщению, его прочитали. Автор в счёт не идёт. */
    @GetMapping("/conversations/{id}/messages/{messageId}/read-status")
    public MessageReadService.ReadStatus readStatus(
            @PathVariable("id") UUID conversationId,
            @PathVariable("messageId") UUID messageId,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return reads.readStatus(viewer.userId(), conversationId, messageId);
    }

    public record ReadRequest(long seq) {
    }
}
