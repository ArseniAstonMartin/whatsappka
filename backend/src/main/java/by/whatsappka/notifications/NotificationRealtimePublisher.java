package by.whatsappka.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Работает в worker, где нет WebSocket: сообщает API по Redis, что уведомление сохранено. Доставка best-effort:
 * если получатель офлайн или Redis недоступен, уведомление всё равно лежит в хранилище и видно через API.
 */
@Component
public class NotificationRealtimePublisher {

    public static final String CHANNEL = "whatsappka:notifications";

    private static final Logger log = LoggerFactory.getLogger(NotificationRealtimePublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public NotificationRealtimePublisher(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /** targetId нужен клиенту, чтобы не показывать уведомление о том чате, который уже открыт. */
    public void published(UUID recipient, UUID notificationId, NotificationType type, UUID targetId) {
        try {
            Map<String, String> signal = new java.util.HashMap<>();
            signal.put("userId", recipient.toString());
            signal.put("notificationId", notificationId.toString());
            signal.put("type", type.name());
            if (targetId != null) {
                signal.put("targetId", targetId.toString());
            }
            String body = mapper.writeValueAsString(signal);
            redis.convertAndSend(CHANNEL, body);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Realtime-сигнал уведомления не отправлен: {}", e.getClass().getSimpleName());
        }
    }
}
