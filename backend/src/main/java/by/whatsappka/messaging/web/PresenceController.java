package by.whatsappka.messaging.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.messaging.ConversationPresence;
import by.whatsappka.platform.web.ApiV1Controller;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PresenceController {

    private final ConversationPresence presence;

    public PresenceController(ConversationPresence presence) {
        this.presence = presence;
    }

    /** Онлайн-участники диалога, кроме самого пользователя. Временное состояние: не история и не хранится в PostgreSQL. */
    @GetMapping("/conversations/{id}/presence")
    public PresenceView presence(@PathVariable("id") UUID conversationId, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return new PresenceView(presence.onlineMembers(viewer.userId(), conversationId));
    }

    public record PresenceView(List<UUID> online) {
    }
}
