package by.whatsappka.platform.ratelimit;

import by.whatsappka.platform.web.ApiException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Единая точка ограничений частоты. При сбое Redis счётчик переходит на локальный и строгий лимит;
 * событие пишется в журнал без содержимого запроса.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RateCounters shared;
    private final RateCounters local;

    public RateLimiter(StringRedisTemplate redis, Clock clock) {
        this.shared = new RedisRateCounters(redis);
        this.local = new LocalRateCounters(clock);
    }

    /** Засчитывает попытку и отвечает 429, если лимит окна исчерпан. */
    public void consume(String name, String key, int limit, Duration window) {
        String counterKey = counterKey(name, key);
        Attempt attempt = attempt(counterKey, window, limit);
        if (!RateLimitPolicy.allowed(attempt.count(), attempt.limit())) {
            throw tooMany(attempt.counters(), counterKey);
        }
    }

    /** Проверяет окно без засчитывания: для входа, где засчитываются только неудачные попытки. */
    public void requireAvailable(String name, String key, int limit) {
        String counterKey = counterKey(name, key);
        RateCounters counters = countersFor(counterKey);
        int effective = counters == local ? RateLimitPolicy.conservative(limit) : limit;
        long count = counters.current(counterKey);
        if (count >= effective) {
            throw tooMany(counters, counterKey);
        }
    }

    /** Засчитывает неудачную попытку (например, неверный пароль). */
    public void recordFailure(String name, String key, Duration window) {
        String counterKey = counterKey(name, key);
        attempt(counterKey, window, Integer.MAX_VALUE);
    }

    private record Attempt(long count, int limit, RateCounters counters) {
    }

    private Attempt attempt(String counterKey, Duration window, int limit) {
        try {
            return new Attempt(shared.increment(counterKey, window), limit, shared);
        } catch (RuntimeException unavailable) {
            log.warn("Redis недоступен для ограничения частоты, применяем локальный лимит: {}",
                    unavailable.getClass().getSimpleName());
            return new Attempt(local.increment(counterKey, window), RateLimitPolicy.conservative(limit), local);
        }
    }

    private RateCounters countersFor(String counterKey) {
        try {
            shared.current(counterKey);
            return shared;
        } catch (RuntimeException unavailable) {
            return local;
        }
    }

    private static String counterKey(String name, String key) {
        return "rl:" + name + ":" + key;
    }

    private static ApiException tooMany(RateCounters counters, String counterKey) {
        Duration retry = RateLimitPolicy.retryAfter(counters.remaining(counterKey));
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests",
                "Слишком много попыток. Подождите и попробуйте снова.", List.of(), retry);
    }
}
