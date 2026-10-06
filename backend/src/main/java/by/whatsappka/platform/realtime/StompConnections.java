package by.whatsappka.platform.realtime;

import by.whatsappka.identity.SessionService;
import by.whatsappka.platform.web.ApiException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * Живые соединения и их авторизация. Проверка сессии идёт на каждом кадре SUBSCRIBE и SEND,
 * а плановый обход закрывает соединения с отозванной сессией, неактивным аккаунтом или истёкшим токеном.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class StompConnections {

    static final CloseStatus SESSION_CLOSED = new CloseStatus(4001, "session_closed");

    record Connection(UUID userId, UUID sessionId, Instant expiresAt) {
    }

    private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    private final Map<String, Connection> authenticated = new ConcurrentHashMap<>();
    private final SessionService sessions;
    private final OnlineStatus online;
    private final Clock clock;

    public StompConnections(SessionService sessions, OnlineStatus online, Clock clock) {
        this.sessions = sessions;
        this.online = online;
        this.clock = clock;
    }

    void opened(WebSocketSession socket) {
        sockets.put(socket.getId(), socket);
    }

    void closed(String sessionId) {
        sockets.remove(sessionId);
        releasePresence(authenticated.remove(sessionId));
    }

    void authenticate(String sessionId, Connection connection) {
        authenticated.put(sessionId, connection);
        online.touch(connection.userId());
    }

    /** Возвращает подтверждённое соединение или бросает ошибку доставки, которую клиент увидит как ERROR-кадр. */
    Connection live(String sessionId) {
        Connection connection = authenticated.get(sessionId);
        if (connection == null) {
            throw new MessageDeliveryException("Соединение не авторизовано");
        }
        if (!clock.instant().isBefore(connection.expiresAt())) {
            throw new MessageDeliveryException("Срок токена истёк");
        }
        if (!isSessionActive(connection)) {
            throw new MessageDeliveryException("Сессия недействительна");
        }
        return connection;
    }

    /** Число подтверждённых соединений: для метрик, без состава пользователей. */
    public int activeCount() {
        return authenticated.size();
    }

    /** Закрывает все подтверждённые соединения пользователя: после смены ролей клиент переподключится и получит новые права. */
    public void closeUser(UUID userId) {
        for (Map.Entry<String, Connection> entry : authenticated.entrySet()) {
            if (entry.getValue().userId().equals(userId)) {
                close(entry.getKey());
            }
        }
    }

    /** Обход также продлевает присутствие тех, у кого соединение остаётся живым. */
    @Scheduled(fixedDelay = 5000)
    public void sweep() {
        Set<UUID> alive = new HashSet<>();
        for (Map.Entry<String, Connection> entry : authenticated.entrySet()) {
            Connection connection = entry.getValue();
            boolean expired = !clock.instant().isBefore(connection.expiresAt());
            if (expired || !isSessionActive(connection)) {
                close(entry.getKey());
            } else {
                alive.add(connection.userId());
            }
        }
        alive.forEach(online::touch);
    }

    private boolean isSessionActive(Connection connection) {
        try {
            sessions.requireActive(connection.userId(), connection.sessionId());
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private void close(String sessionId) {
        WebSocketSession socket = sockets.get(sessionId);
        releasePresence(authenticated.remove(sessionId));
        if (socket == null) {
            return;
        }
        try {
            socket.close(SESSION_CLOSED);
        } catch (IOException ignored) {
            // Соединение уже оборвано: запись в карте очищается при закрытии транспорта.
        }
    }

    /** Присутствие гаснет, когда у пользователя не осталось ни одного живого соединения. */
    private void releasePresence(Connection removed) {
        if (removed != null && authenticated.values().stream().noneMatch(c -> c.userId().equals(removed.userId()))) {
            online.clear(removed.userId());
        }
    }
}
