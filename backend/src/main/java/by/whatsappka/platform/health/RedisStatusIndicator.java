package by.whatsappka.platform.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis: недоступность отмечается деградацией, readiness API от неё не зависит. Заодно отдаёт объём памяти как метрику. */
@Component("redis")
public class RedisStatusIndicator implements HealthIndicator {

    private final StringRedisTemplate redis;
    private final DependencyStatus status;
    private final AtomicLong usedMemory = new AtomicLong(-1);

    public RedisStatusIndicator(StringRedisTemplate redis, MeterRegistry metrics, Clock clock) {
        this.redis = redis;
        this.status = new DependencyStatus(clock);
        Gauge.builder("whatsappka.redis.used.memory.bytes", usedMemory, AtomicLong::get).register(metrics);
    }

    @Override
    public Health health() {
        return status.check(this::probe);
    }

    private Health probe() {
        try {
            Properties memory = redis.execute((RedisCallback<Properties>) connection -> connection.serverCommands().info("memory"));
            long used = memory == null ? -1 : Long.parseLong(memory.getProperty("used_memory", "-1"));
            usedMemory.set(used);
            return Health.up().withDetail("usedMemoryBytes", used).build();
        } catch (RuntimeException e) {
            // Текст ошибки может содержать адрес сервера: в ответ уходит только класс исключения.
            usedMemory.set(-1);
            return DependencyStatus.degraded(e.getClass().getSimpleName());
        }
    }
}
