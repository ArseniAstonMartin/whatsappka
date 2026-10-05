package by.whatsappka.messaging;

import by.whatsappka.platform.realtime.OnlineStatus;
import by.whatsappka.platform.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

/** Кто из участников диалога сейчас онлайн. Видно только участникам; при недоступном Redis список пуст. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConversationPresence {

    private final ConversationGuard guard;
    private final MessageService messages;
    private final OnlineStatus online;

    public ConversationPresence(ConversationGuard guard, MessageService messages, OnlineStatus online) {
        this.guard = guard;
        this.messages = messages;
        this.online = online;
    }

    public List<UUID> onlineMembers(UUID viewer, UUID conversationId) {
        if (!guard.isActiveMember(conversationId, viewer)) {
            throw ApiException.notFound();
        }
        if (guard.blockedInDirect(viewer, conversationId)) {
            return List.of();
        }
        return messages.recipients(conversationId, viewer).stream().filter(online::isOnline).toList();
    }
}
