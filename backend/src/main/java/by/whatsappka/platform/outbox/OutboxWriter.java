package by.whatsappka.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Записывает событие в текущей транзакции. Сети здесь нет: сигнал уходит только после commit.
 */
@Service
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final OutboxSignals signals;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock, OutboxSignals signals) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.signals = signals;
    }

    public UUID record(String aggregateType, UUID aggregateId, String type, Object payload) {
        requireLength(aggregateType, 40, "aggregateType");
        requireLength(type, 80, "type");
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                OutboxSql.INSERT,
                eventId, aggregateType, aggregateId, type, toJson(payload),
                Timestamp.from(now), Timestamp.from(now)
        );
        afterCommit(signals::publish);
        return eventId;
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private String toJson(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Полезная нагрузка события не сериализуется", e);
        }
    }

    private static void requireLength(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("Поле " + field + " должно быть от 1 до " + max + " символов");
        }
    }
}
