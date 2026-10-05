package by.whatsappka.identity.recovery;

import by.whatsappka.identity.session.RefreshTokens;
import by.whatsappka.platform.web.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Одноразовые токены подтверждения почты и сброса пароля. Клиенту уходит случайное значение,
 * в базе только SHA-256 хеш (тот же способ, что у refresh-токенов), срок и отметка использования.
 */
@Service
public class AccountTokens {

    public static final String EMAIL_CONFIRMATION = "EMAIL_CONFIRMATION";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AccountTokens(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Выпускает новый токен и возвращает его значение для письма. Значение нигде больше не сохраняется. */
    public String issue(UUID userId, String purpose, Duration lifetime) {
        Instant now = clock.instant();
        jdbc.update(AccountTokenSql.RETIRE_OPEN, Timestamp.from(now), userId, purpose);
        String token = RefreshTokens.generate();
        jdbc.update(AccountTokenSql.INSERT, UUID.randomUUID(), userId, purpose, RefreshTokens.hash(token),
                Timestamp.from(now.plus(lifetime)), Timestamp.from(now));
        return token;
    }

    /** Принимает токен один раз: возвращает владельца, если токен действителен; иначе пусто. */
    public Optional<UUID> consume(String purpose, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String hash = RefreshTokens.hash(token);
        Instant now = clock.instant();
        List<Row> rows = jdbc.query(AccountTokenSql.FIND_FOR_UPDATE, (rs, row) -> new Row(
                UUID.fromString(rs.getString("user_id")),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("consumed_at") != null
        ), hash, purpose);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Row row = rows.get(0);
        if (row.consumed() || !row.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        jdbc.update(AccountTokenSql.MARK_CONSUMED, Timestamp.from(now), hash, purpose);
        return Optional.of(row.userId());
    }

    /** Одна ошибка для любого неверного, использованного или просроченного токена: различия не раскрываются. */
    public static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_token",
                "Ссылка недействительна или срок её действия истёк", List.of(), null);
    }

    private record Row(UUID userId, Instant expiresAt, boolean consumed) {
    }
}
