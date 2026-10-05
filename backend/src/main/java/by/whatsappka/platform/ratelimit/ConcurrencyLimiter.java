package by.whatsappka.platform.ratelimit;

import by.whatsappka.platform.web.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Одновременные загрузки одного пользователя (PRD: не более двух). Локально для экземпляра: одновременность
 * — состояние конкретного запроса, его не нужно делить через Redis между процессами.
 */
@Component
public class ConcurrencyLimiter {

    private final Map<String, Integer> active = new ConcurrentHashMap<>();

    /** Занимает слот или отвечает 429. Освобождение закрывает слот; закрывать нужно в finally. */
    public Lease acquire(String key, int max) {
        AtomicBoolean taken = new AtomicBoolean(false);
        active.compute(key, (k, n) -> {
            int current = n == null ? 0 : n;
            if (current < max) {
                taken.set(true);
                return current + 1;
            }
            return current;
        });
        if (!taken.get()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests",
                    "Одновременно можно загрузить не больше двух файлов.", List.of(), Duration.ofSeconds(5));
        }
        return () -> release(key);
    }

    private void release(String key) {
        active.computeIfPresent(key, (k, n) -> n <= 1 ? null : n - 1);
    }

    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }
}
