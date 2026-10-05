package by.whatsappka.platform.ratelimit;

import java.time.Duration;

/** Чистая логика окна: сравнение счётчика с лимитом и консервативный лимит при недоступном Redis. */
public final class RateLimitPolicy {

    public static final Duration MINUTE = Duration.ofMinutes(1);
    public static final Duration HOUR = Duration.ofHours(1);

    /** Минимальная задержка для Retry-After: клиент не должен получить 0 секунд. */
    static final Duration MIN_RETRY = Duration.ofSeconds(1);

    private RateLimitPolicy() {
    }

    /** Попытка разрешена, пока счётчик не превысил лимит (лимит — число разрешённых попыток в окне). */
    public static boolean allowed(long count, int limit) {
        return count <= limit;
    }

    /** Когда Redis недоступен, лимит делается строже: половина, но не меньше одной попытки. */
    public static int conservative(int limit) {
        return Math.max(1, limit / 2);
    }

    /** Ключ входа: адрес и почта. Ответ одинаков, существует аккаунт или нет, поэтому ключ не раскрывает это. */
    public static String loginKey(String clientAddress, String email) {
        return clientAddress + "|" + (email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static Duration retryAfter(Duration remaining) {
        if (remaining == null || remaining.compareTo(MIN_RETRY) < 0) {
            return MIN_RETRY;
        }
        return remaining;
    }
}
