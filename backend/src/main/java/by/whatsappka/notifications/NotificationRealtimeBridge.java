package by.whatsappka.notifications;

import by.whatsappka.platform.realtime.RealtimeEvent;
import by.whatsappka.platform.realtime.RealtimePublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/** В API: получает сигнал worker'а и доставляет его владельцу, если он онлайн. Содержимого уведомления в сигнале нет. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class NotificationRealtimeBridge implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationRealtimeBridge.class);

    private final RealtimePublisher realtime;
    private final ObjectMapper mapper;

    public NotificationRealtimeBridge(RealtimePublisher realtime, ObjectMapper mapper) {
        this.realtime = realtime;
        this.mapper = mapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode body = mapper.readTree(message.getBody());
            UUID userId = UUID.fromString(body.get("userId").asText());
            UUID notificationId = UUID.fromString(body.get("notificationId").asText());
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("id", notificationId.toString());
            payload.put("type", body.get("type").asText());
            if (body.hasNonNull("targetId")) {
                payload.put("targetId", body.get("targetId").asText());
            }
            realtime.deliver(userId, new RealtimeEvent(UUID.randomUUID(), "notification.created", Instant.now(),
                    notificationId, 0L, null, null, payload));
        } catch (Exception e) {
            log.warn("Некорректный сигнал уведомления пропущен: {}", e.getClass().getSimpleName());
        }
    }
}
