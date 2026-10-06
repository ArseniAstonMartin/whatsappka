package by.whatsappka.platform.redis;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Подписки Redis поднимаются не при старте контекста, а по расписанию. Иначе недоступный Redis не даёт API
 * запуститься: контейнер подписки синхронно подключается при старте. Пока Redis недоступен, сигналы не приходят,
 * и работа остаётся на плановом опросе БД; после восстановления подписка включается сама.
 */
@Component
public class RedisListenerStarter {

    private static final Logger log = LoggerFactory.getLogger(RedisListenerStarter.class);

    private final List<RedisMessageListenerContainer> containers;

    public RedisListenerStarter(List<RedisMessageListenerContainer> containers) {
        this.containers = containers;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 0)
    public void ensureRunning() {
        for (RedisMessageListenerContainer container : containers) {
            if (container.isRunning()) {
                continue;
            }
            try {
                container.start();
                log.info("Подписка Redis включена");
            } catch (RuntimeException e) {
                // Текст ошибки может содержать адрес Redis: в журнал идёт только класс исключения.
                log.warn("Подписка Redis пока недоступна: {}", e.getClass().getSimpleName());
            }
        }
    }
}
