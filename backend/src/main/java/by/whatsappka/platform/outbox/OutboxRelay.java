package by.whatsappka.platform.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Доставляет события consumer'ам. Каждый consumer получает событие один раз после успеха:
 * при повторе работают только те, кто не подтвердил доставку. Захват и фиксация идут короткими транзакциями.
 */
@Component
@ConditionalOnProperty(prefix = "whatsappka.outbox", name = "relay-enabled", havingValue = "true")
public class OutboxRelay {

    /** Число попыток по событию, после которого оно уходит в FAILED. */
    static final int MAX_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int REASON_MAX = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final List<OutboxConsumer> consumers;
    private final OutboxProperties properties;
    private final String relayId;

    public OutboxRelay(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            ObjectMapper mapper,
            List<OutboxConsumer> consumers,
            OutboxProperties properties
    ) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.mapper = mapper;
        this.consumers = validate(consumers);
        this.properties = properties;
        this.relayId = "relay-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Опрос по расписанию и по сигналу Redis. Синхронизирован, чтобы два опроса не брали одну партию. */
    @Scheduled(fixedDelay = 1000)
    public synchronized void poll() {
        tx.executeWithoutResult(status -> jdbc.update(OutboxSql.FAIL_EXHAUSTED_LEASES, MAX_ATTEMPTS));
        List<Claimed> batch = tx.execute(status -> jdbc.query(
                OutboxSql.CLAIM,
                (rs, row) -> new Claimed(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("aggregate_type"),
                        UUID.fromString(rs.getString("aggregate_id")),
                        rs.getString("type"),
                        rs.getString("payload"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getInt("attempts")
                ),
                MAX_ATTEMPTS,
                properties.batchSize(),
                relayId,
                properties.lease().toSeconds()
        ));
        for (Claimed event : batch) {
            process(event);
        }
    }

    private void process(Claimed event) {
        try {
            OutboxEnvelope envelope = new OutboxEnvelope(
                    event.id(),
                    event.type(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.occurredAt(),
                    mapper.readTree(event.payload())
            );
            Set<String> delivered = delivered(event.id());
            for (OutboxConsumer consumer : consumers) {
                if (!consumer.eventTypes().contains(event.type()) || delivered.contains(consumer.name())) {
                    continue;
                }
                consumer.consume(envelope);
                recordDelivery(event.id(), consumer.name());
            }
            fixate(OutboxSql.COMPLETE, event.id(), null);
        } catch (Exception e) {
            String reason = reason(e);
            if (event.attempts() >= MAX_ATTEMPTS) {
                fixate(OutboxSql.FAIL, event.id(), reason);
            } else {
                retryLater(event, reason);
            }
        }
    }

    private Set<String> delivered(UUID eventId) {
        return new HashSet<>(jdbc.queryForList(OutboxSql.DELIVERED_CONSUMERS, String.class, eventId));
    }

    private void recordDelivery(UUID eventId, String consumer) {
        tx.executeWithoutResult(status -> jdbc.update(OutboxSql.RECORD_DELIVERY, eventId, consumer));
    }

    private void retryLater(Claimed event, String reason) {
        long delaySeconds = Duration.ofSeconds(30L << Math.min(event.attempts() - 1, 5)).toSeconds();
        int updated = tx.execute(status -> jdbc.update(
                OutboxSql.RETRY_LATER, reason, delaySeconds, event.id(), relayId));
        logOwnership(event, updated, "повтор через " + delaySeconds + " с");
    }

    /** COMPLETE или FAIL с проверкой владельца аренды: потерянную аренду не перетираем. */
    private void fixate(String sql, UUID eventId, String reason) {
        int updated = tx.execute(status -> reason == null
                ? jdbc.update(sql, eventId, relayId)
                : jdbc.update(sql, reason, eventId, relayId));
        if (updated == 0) {
            log.warn("Аренда события {} потеряна, результат не записан", eventId);
        }
    }

    private void logOwnership(Claimed event, int updated, String outcome) {
        if (updated == 0) {
            log.warn("Аренда события {} потеряна, {} не записан", event.id(), outcome);
        } else {
            log.info("Событие {} ({}): {}", event.id(), event.type(), outcome);
        }
    }

    private static List<OutboxConsumer> validate(List<OutboxConsumer> consumers) {
        Set<String> names = new HashSet<>();
        for (OutboxConsumer consumer : consumers) {
            if (!names.add(consumer.name())) {
                throw new IllegalStateException("Два consumer'а outbox с именем " + consumer.name());
            }
        }
        return List.copyOf(consumers);
    }

    private static String reason(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > REASON_MAX ? message.substring(0, REASON_MAX) : message;
    }

    private record Claimed(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String type,
            String payload,
            Instant occurredAt,
            int attempts
    ) {
    }
}
