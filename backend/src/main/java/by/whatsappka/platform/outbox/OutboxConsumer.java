package by.whatsappka.platform.outbox;

import java.util.Set;

/**
 * Получатель событий. Транспорт не знает, что делает consumer: SMTP, уведомления или realtime.
 * Повторный вызов с тем же eventId должен быть безопасен, потому что доставка выполняется как минимум один раз.
 */
public interface OutboxConsumer {

    /** Уникальное имя в пределах приложения: по нему хранится факт доставки. */
    String name();

    Set<String> eventTypes();

    void consume(OutboxEnvelope event) throws Exception;
}
