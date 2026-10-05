package by.whatsappka.platform.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Локальные окна в памяти процесса. Не общие между экземплярами, поэтому используются только как запасной вариант. */
public final class LocalRateCounters implements RateCounters {

    private record Window(long count, Instant endsAt) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public LocalRateCounters(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long increment(String key, Duration window) {
        Instant now = clock.instant();
        return windows.compute(key, (k, current) -> {
            if (current == null || !now.isBefore(current.endsAt())) {
                return new Window(1, now.plus(window));
            }
            return new Window(current.count() + 1, current.endsAt());
        }).count();
    }

    @Override
    public long current(String key) {
        Window window = windows.get(key);
        if (window == null || !clock.instant().isBefore(window.endsAt())) {
            return 0;
        }
        return window.count();
    }

    @Override
    public Duration remaining(String key) {
        Window window = windows.get(key);
        if (window == null) {
            return Duration.ZERO;
        }
        Duration left = Duration.between(clock.instant(), window.endsAt());
        return left.isNegative() ? Duration.ZERO : left;
    }
}
