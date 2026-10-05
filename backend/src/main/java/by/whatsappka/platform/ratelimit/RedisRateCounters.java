package by.whatsappka.platform.ratelimit;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Общие счётчики в Redis: INCR, а срок окна ставится при первой попытке. */
public final class RedisRateCounters implements RateCounters {

    private final StringRedisTemplate redis;

    public RedisRateCounters(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration window) {
        Long value = redis.opsForValue().increment(key);
        if (value == null) {
            throw new IllegalStateException("Redis не вернул значение счётчика");
        }
        if (value == 1L) {
            redis.expire(key, window);
        }
        return value;
    }

    @Override
    public long current(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    @Override
    public Duration remaining(String key) {
        Long seconds = redis.getExpire(key);
        return seconds == null || seconds < 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }
}
