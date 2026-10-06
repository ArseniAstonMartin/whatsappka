package by.whatsappka.platform.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.boot.actuate.health.Health;

/**
 * Результат проверки зависимости, который кэшируется на короткий срок. Пробы Kubernetes-подобного вида и
 * панель оператора не должны обращаться к Redis и MinIO на каждый запрос.
 */
final class DependencyStatus {

    /** Статус для недоступной, но не критичной зависимости: API продолжает отвечать, интерфейс предупреждает. */
    static final String DEGRADED = "DEGRADED";

    static final Duration TTL = Duration.ofSeconds(10);

    private final Clock clock;
    private volatile Instant checkedAt = Instant.EPOCH;
    private volatile Health last = Health.unknown().build();

    DependencyStatus(Clock clock) {
        this.clock = clock;
    }

    Health check(Supplier<Health> probe) {
        Instant now = clock.instant();
        if (now.isBefore(checkedAt.plus(TTL))) {
            return last;
        }
        last = probe.get();
        checkedAt = now;
        return last;
    }

    static Health degraded(String reason) {
        return Health.status(DEGRADED).withDetail("reason", reason).build();
    }
}
