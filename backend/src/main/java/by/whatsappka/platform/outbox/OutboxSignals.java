package by.whatsappka.platform.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis только сообщает, что в outbox есть новые события. Истина — PostgreSQL:
 * потерянный сигнал не теряет событие, его заберёт плановый опрос worker'а.
 */
@Component
public class OutboxSignals {

    public static final String CHANNEL = "whatsappka:outbox";

    private static final Logger log = LoggerFactory.getLogger(OutboxSignals.class);

    private final StringRedisTemplate redis;

    public OutboxSignals(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publish() {
        try {
            redis.convertAndSend(CHANNEL, "new");
        } catch (RuntimeException e) {
            // Недоступный Redis не должен мешать работе с БД. Событие уже сохранено.
            log.warn("Сигнал outbox не отправлен, событие будет взято плановым опросом: {}", e.getClass().getSimpleName());
        }
    }
}
