package by.whatsappka.identity.recovery;

import by.whatsappka.identity.RegistrationService;
import by.whatsappka.identity.SessionService;
import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сброс пароля. Ответ на запрос одинаков для любого адреса: письмо уходит только существующему активному аккаунту,
 * и никакой записи для неизвестного адреса не делается. Успешный сброс отзывает все сессии.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PasswordResetService {

    static final Duration LIFETIME = Duration.ofHours(1);

    private final UserAccountRepository users;
    private final AccountTokens tokens;
    private final AccountMails mails;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PasswordResetService(
            UserAccountRepository users,
            AccountTokens tokens,
            AccountMails mails,
            SessionService sessions,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.users = users;
        this.tokens = tokens;
        this.mails = mails;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void request(String email) {
        Optional<UserAccount> user = users.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .filter(UserAccount::isActive);
        user.ifPresent(account -> {
            String token = tokens.issue(account.id(), AccountTokens.PASSWORD_RESET, LIFETIME);
            mails.queueLink(account.email(), "Сброс пароля в WhatsAppka",
                    "Чтобы задать новый пароль, откройте ссылку:",
                    "/reset-password", token, LIFETIME);
        });
    }

    /** Проверка пароля идёт до погашения токена: ошибка формы не тратит ссылку. */
    @Transactional
    public void reset(String token, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > RegistrationService.PASSWORD_MAX_BYTES) {
            throw ApiException.validation(
                    "Проверьте поля запроса",
                    List.of(new FieldErrorDetail("password", "Пароль длиннее 72 байт в UTF-8"))
            );
        }
        UUID userId = tokens.consume(AccountTokens.PASSWORD_RESET, token).orElseThrow(AccountTokens::invalidToken);
        UserAccount user = users.findById(userId).filter(UserAccount::isActive).orElseThrow(AccountTokens::invalidToken);
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword), now);
        // Письмо, пришедшее на адрес, подтверждает владение им: отдельное подтверждение не нужно.
        user.markEmailVerified(now);
        sessions.revokeAll(user.id());
    }
}
