package by.whatsappka.platform.realtime;

import by.whatsappka.identity.security.AccessService;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.identity.token.AccessTokenVerifier;
import by.whatsappka.platform.web.ApiException;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Проверяет входящие кадры до маршрутизации: CONNECT — токен и сессия, SUBSCRIBE и SEND — allowlist и живое соединение.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class StompInboundGuard implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final AccessService access;
    private final AccessTokenVerifier tokens;
    private final StompConnections connections;

    public StompInboundGuard(AccessService access, AccessTokenVerifier tokens, StompConnections connections) {
        this.access = access;
        this.tokens = tokens;
        this.connections = connections;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        String sessionId = accessor.getSessionId();
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            connect(accessor, sessionId);
        } else if (command == StompCommand.SUBSCRIBE) {
            connections.live(sessionId);
            if (!StompRules.subscribable(accessor.getDestination())) {
                throw denied();
            }
        } else if (command == StompCommand.SEND) {
            connections.live(sessionId);
            if (!StompRules.sendable(accessor.getDestination())) {
                throw denied();
            }
        }
        return message;
    }

    private void connect(StompHeaderAccessor accessor, String sessionId) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessageDeliveryException("Нужна действующая сессия");
        }
        try {
            AuthenticatedUser user = access.authenticate(header);
            Instant expiresAt = tokens.expiresAt(header.substring(BEARER.length()).trim());
            connections.authenticate(sessionId, new StompConnections.Connection(user.userId(), user.sessionId(), expiresAt));
            accessor.setUser(new StompPrincipal(user.userId()));
        } catch (ApiException e) {
            throw new MessageDeliveryException("Нужна действующая сессия");
        }
    }

    private static MessageDeliveryException denied() {
        return new MessageDeliveryException("Команда недоступна");
    }
}
