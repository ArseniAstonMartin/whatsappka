package by.whatsappka.identity;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.identity.session.AuthSession;
import by.whatsappka.identity.session.AuthSessionRepository;
import by.whatsappka.identity.session.RefreshToken;
import by.whatsappka.identity.session.RefreshTokenRepository;
import by.whatsappka.identity.session.RefreshTokens;
import by.whatsappka.identity.token.AccessTokenIssuer;
import by.whatsappka.identity.token.AccessTokenVerifier;
import by.whatsappka.platform.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ротация refresh-токенов, выход и управление сессиями пользователя. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SessionService {

    private static final String BEARER = "Bearer ";

    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final UserAccountRepository users;
    private final AccessTokenIssuer accessTokens;
    private final AccessTokenVerifier accessTokenVerifier;
    private final Clock clock;

    public SessionService(
            AuthSessionRepository sessions,
            RefreshTokenRepository refreshTokens,
            UserAccountRepository users,
            AccessTokenIssuer accessTokens,
            AccessTokenVerifier accessTokenVerifier,
            Clock clock
    ) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.accessTokens = accessTokens;
        this.accessTokenVerifier = accessTokenVerifier;
        this.clock = clock;
    }

    /**
     * Заменяет refresh-токен. Ошибка выбрасывается после записи отзыва сессии при повторе старого токена,
     * поэтому откат транзакции для ApiException отключён.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefresh();
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHashForUpdate(RefreshTokens.hash(refreshToken))
                .orElseThrow(SessionService::invalidRefresh);
        AuthSession session = sessions.findById(current.sessionId()).orElseThrow(SessionService::invalidRefresh);

        if (current.isUsed()) {
            // Повтор уже заменённого токена означает утечку: отзываем всё семейство (сессию).
            session.revoke(now);
            throw invalidRefresh();
        }
        if (!session.isActiveAt(now) || !current.isValidAt(now)) {
            throw invalidRefresh();
        }
        UserAccount user = users.findById(session.userId())
                .filter(UserAccount::isActive)
                .orElseThrow(SessionService::invalidRefresh);

        String nextToken = RefreshTokens.generate();
        RefreshToken replacement = new RefreshToken(
                UUID.randomUUID(),
                session.id(),
                RefreshTokens.hash(nextToken),
                session.absoluteExpiresAt(),
                now
        );
        refreshTokens.save(replacement);
        current.markUsed(now, replacement.id());
        session.touch(now);

        String accessToken = accessTokens.issue(user.id(), session.id());
        return new Rotation(user, accessToken, nextToken, session.absoluteExpiresAt());
    }

    /** Отзывает сессию, к которой относится refresh-токен. Неизвестный токен не даёт ошибки. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(RefreshTokens.hash(refreshToken))
                .flatMap(token -> sessions.findById(token.sessionId()))
                .ifPresent(session -> session.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public AuthenticatedSession authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            throw ApiException.unauthorized();
        }
        AuthenticatedSession claims = accessTokenVerifier.verify(authorization.substring(BEARER.length()).trim());
        AuthSession session = sessions.findById(claims.sessionId())
                .filter(s -> s.userId().equals(claims.userId()) && s.isActiveAt(clock.instant()))
                .orElseThrow(ApiException::unauthorized);
        UserAccount user = users.findById(session.userId())
                .filter(UserAccount::isActive)
                .orElseThrow(ApiException::unauthorized);
        return new AuthenticatedSession(user.id(), session.id());
    }

    @Transactional(readOnly = true)
    public List<AuthSession> listActive(UUID userId) {
        return sessions.findActiveByUserId(userId, clock.instant());
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        AuthSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(ApiException::notFound);
        session.revoke(clock.instant());
    }

    @Transactional
    public void revokeAll(UUID userId) {
        Instant now = clock.instant();
        sessions.findActiveByUserId(userId, now).forEach(session -> session.revoke(now));
    }

    private static ApiException invalidRefresh() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "invalid_refresh_token",
                "Сессия не действительна, войдите снова",
                List.of(),
                null
        );
    }

    public record Rotation(UserAccount user, String accessToken, String refreshToken, Instant refreshExpiresAt) {
    }
}
