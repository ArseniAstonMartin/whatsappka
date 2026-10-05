package by.whatsappka.platform.idempotency;

import by.whatsappka.platform.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyRecords {

    public static final Duration RETENTION = Duration.ofHours(24);

    private static final Pattern OPERATION = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,99}$");
    private static final Pattern KEY = Pattern.compile("^[\\x21-\\x7E]{1,255}$");

    private final IdempotencyRecordRepository records;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyRecords(
            IdempotencyRecordRepository records,
            EntityManager entityManager,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.records = records;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public IdempotentResult execute(
            UUID userId,
            String operation,
            String key,
            byte[] requestContent,
            Supplier<IdempotentResponse> action
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("Пользователь идемпотентности не задан");
        }
        if (operation == null || !OPERATION.matcher(operation).matches()) {
            throw new IllegalArgumentException("Некорректное имя операции идемпотентности");
        }
        if (key == null || !KEY.matcher(key).matches()) {
            throw ApiException.badRequest(
                    "bad_request",
                    "Заголовок Idempotency-Key должен содержать от 1 до 255 видимых символов ASCII"
            );
        }
        if (action == null) {
            throw new IllegalArgumentException("Действие идемпотентности не задано");
        }

        lock(userId, operation, key);
        String requestHash = sha256(requestContent == null ? new byte[0] : requestContent);
        IdempotencyRecordId id = new IdempotencyRecordId(userId, operation, key);
        Optional<IdempotencyRecord> current = records.findById(id);
        Instant now = clock.instant();
        if (current.isPresent() && !current.get().expiresAt().isAfter(now)) {
            records.delete(current.get());
            records.flush();
            current = Optional.empty();
        }
        if (current.isPresent()) {
            IdempotencyRecord stored = current.get();
            if (!stored.requestHash().equals(requestHash)) {
                throw ApiException.conflict("Ключ идемпотентности уже использован для другого запроса");
            }
            return new IdempotentResult(stored.responseStatus(), readBody(stored.responseBody()), true);
        }

        IdempotentResponse produced = action.get();
        if (produced == null) {
            throw new IllegalArgumentException("Действие идемпотентности не вернуло ответ");
        }
        String responseBody = writeBody(produced.body());
        Instant expiresAt = now.plus(RETENTION);
        records.saveAndFlush(new IdempotencyRecord(id, requestHash, produced.status(), responseBody, expiresAt));
        return new IdempotentResult(produced.status(), readBody(responseBody), false);
    }

    private void lock(UUID userId, String operation, String key) {
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtext(:userId), hashtext(:scope)) is null"
                )
                .setParameter("userId", userId.toString())
                .setParameter("scope", operation + "\u001f" + key)
                .getSingleResult();
    }

    private String writeBody(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Ответ идемпотентности не сериализуется в JSON", exception);
        }
    }

    private JsonNode readBody(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Сохранённый ответ идемпотентности повреждён", exception);
        }
    }

    static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }

    public static byte[] utf8(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
