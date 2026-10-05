package by.whatsappka.messaging.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.messaging.MessageQueries;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MessageController {

    private final MessageQueries messages;

    public MessageController(MessageQueries messages) {
        this.messages = messages;
    }

    /** История чата: новые сообщения первыми, по 50. cursor — seq последнего показанного. */
    @GetMapping("/conversations/{id}/messages")
    public CursorPage<MessageQueries.MessageView> history(
            @PathVariable("id") UUID conversationId,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return messages.history(viewer.userId(), conversationId, cursor, limit);
    }
}
