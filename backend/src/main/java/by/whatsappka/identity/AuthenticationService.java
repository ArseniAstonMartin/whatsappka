package by.whatsappka.identity;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.platform.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Вход по email и паролю. Сессию открывает {@link SessionService}. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthenticationService {

    private static final String INVALID_CREDENTIALS = "Неверный email или пароль";

    private final UserAccountRepository users;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String absentUserHash;

    public AuthenticationService(
            UserAccountRepository users,
            SessionService sessions,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        // Хеш-заглушка выравнивает время ответа, когда аккаунта с таким email нет.
        this.absentUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult login(String email, String password, String deviceLabel) {
        Optional<UserAccount> found = users.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT));
        UserAccount user = found.orElse(null);
        boolean sizeAllowed = password.getBytes(StandardCharsets.UTF_8).length <= RegistrationService.PASSWORD_MAX_BYTES;
        String storedHash = user == null || user.passwordHash() == null ? absentUserHash : user.passwordHash();
        boolean passwordMatches = sizeAllowed && passwordEncoder.matches(password, storedHash);
        if (user == null || user.passwordHash() == null || !passwordMatches) {
            throw invalidCredentials();
        }
        if (user.isSuspended()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "account_suspended", "Аккаунт ограничен модерацией", List.of(), null);
        }
        if (!user.isActive()) {
            // Статус проверяется только после верного пароля, чтобы не раскрывать существование аккаунта.
            throw new ApiException(HttpStatus.FORBIDDEN, "account_disabled", "Аккаунт отключён", List.of(), null);
        }

        user.recordActivity(clock.instant());
        SessionService.Opened opened = sessions.open(user, deviceLabel);
        return new LoginResult(user, opened.accessToken(), opened.refreshToken(), opened.refreshExpiresAt());
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", INVALID_CREDENTIALS, List.of(), null);
    }

    public record LoginResult(UserAccount user, String accessToken, String refreshToken, Instant refreshExpiresAt) {
    }
}
