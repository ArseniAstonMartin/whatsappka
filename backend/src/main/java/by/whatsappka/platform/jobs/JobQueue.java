package by.whatsappka.platform.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Постановка заданий. Вызывается внутри бизнес-транзакции: задание появляется вместе с изменением данных.
 * Повторная постановка с тем же dedupKey ничего не делает.
 */
@Service
public class JobQueue {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public JobQueue(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Возвращает true, если задание поставлено; false, если такое уже есть. */
    public boolean enqueue(String type, String dedupKey, Object payload, Instant runAt) {
        requireLength(type, 40, "type");
        requireLength(dedupKey, 255, "dedupKey");
        String json = toJson(payload);
        return jdbc.update(BackgroundJobSql.ENQUEUE, UUID.randomUUID(), type, dedupKey, json, Timestamp.from(runAt)) == 1;
    }

    public boolean enqueue(String type, String dedupKey, Object payload) {
        return enqueue(type, dedupKey, payload, clock.instant());
    }

    /** Переводит FAILED обратно в очередь с нулевым счётчиком попыток. Возвращает false, если задание не в FAILED. */
    public boolean retryFailed(UUID jobId) {
        return jdbc.update(BackgroundJobSql.OPERATOR_RETRY, jobId) == 1;
    }

    /** Отмена: снимает ещё не взятое в работу задание с этим ключом. Активное (RUNNING) не трогает. */
    public void cancel(String dedupKey) {
        jdbc.update(BackgroundJobSql.CANCEL_QUEUED, dedupKey);
    }

    /**
     * Снимает завершённую запись (DONE/FAILED) с этим ключом, чтобы повторная постановка того же дела
     * не упёрлась в уникальность dedup_key. Активное (RUNNING) не трогает.
     */
    public void clearFinished(String dedupKey) {
        jdbc.update(BackgroundJobSql.CLEAR_FINISHED, dedupKey);
    }

    private String toJson(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Полезная нагрузка задания не сериализуется", e);
        }
    }

    private static void requireLength(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("Поле " + field + " должно быть от 1 до " + max + " символов");
        }
    }
}
