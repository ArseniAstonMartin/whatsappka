package by.whatsappka.platform.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Короткоживущий кэш безопасных публичных полей (профиль, позже — метаданные групп).
 *
 * Хранит только то, что можно показать любому: не авторизацию, не санкции, не session state
 * и не приватные данные — эти решения всегда проверяются заново на PostgreSQL. Недоступный Redis
 * не прерывает запрос: {@link #get(String, Class)} возвращает {@link Optional#empty()}, и вызывающий
 * код читает исходные данные напрямую из PostgreSQL.
 */
@Component
public class PublicFieldsCache {

    public static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(PublicFieldsCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public PublicFieldsCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Кэш публичных полей недоступен для {}, читаем источник: {}", key, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void put(String key, Object value) {
        put(key, value, DEFAULT_TTL);
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Не удалось записать кэш публичных полей {}: {}", key, e.getClass().getSimpleName());
        }
    }

    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("Не удалось инвалидировать кэш публичных полей {}: {}", key, e.getClass().getSimpleName());
        }
    }
}
