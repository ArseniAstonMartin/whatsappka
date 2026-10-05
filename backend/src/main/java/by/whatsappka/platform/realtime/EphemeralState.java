package by.whatsappka.platform.realtime;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Короткоживущие ключи realtime в Redis: набор текста, присутствие. Срок жизни задаёт TTL, поэтому устаревшее состояние
 * исчезает само. Недоступный Redis не прерывает запрос: состояние не сохраняется, индикаторы просто не показываются.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class EphemeralState {

    private static final Logger log = LoggerFactory.getLogger(EphemeralState.class);

    private final StringRedisTemplate redis;

    public EphemeralState(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Захватывает ключ на срок ttl. Возвращает false, если ключ уже занят или Redis недоступен. */
    public boolean claim(String key, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
        } catch (RuntimeException e) {
            log.warn("Состояние realtime недоступно, сигнал пропущен: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public void mark(String key, Duration ttl) {
        try {
            redis.opsForValue().set(key, "1", ttl);
        } catch (RuntimeException e) {
            log.warn("Состояние realtime не записано: {}", e.getClass().getSimpleName());
        }
    }

    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void remove(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("Состояние realtime не снято, истечёт по сроку: {}", e.getClass().getSimpleName());
        }
    }
}
