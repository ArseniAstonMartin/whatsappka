package by.whatsappka.identity;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.identity.account.UserProfile;
import by.whatsappka.identity.account.UserProfileRepository;
import by.whatsappka.identity.account.UserRoleGrant;
import by.whatsappka.identity.account.UserRoleId;
import by.whatsappka.identity.account.UserRoleRepository;
import by.whatsappka.identity.recovery.EmailConfirmationService;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Создание аккаунтов: регистрация по паролю и аккаунт из Google. Пользователь, профиль и роль USER создаются одной транзакцией. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RegistrationService {

    public static final int PASSWORD_MAX_BYTES = 72;
    private static final String DUPLICATE_DETAIL = "Не удалось зарегистрировать аккаунт с такими данными";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository users;
    private final UserProfileRepository profiles;
    private final UserRoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final EmailConfirmationService emailConfirmation;
    private final Clock clock;

    public RegistrationService(
            UserAccountRepository users,
            UserProfileRepository profiles,
            UserRoleRepository roles,
            PasswordEncoder passwordEncoder,
            EmailConfirmationService emailConfirmation,
            Clock clock
    ) {
        this.users = users;
        this.profiles = profiles;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.emailConfirmation = emailConfirmation;
        this.clock = clock;
    }

    @Transactional
    public UserAccount register(String email, String username, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw ApiException.validation(
                    "Проверьте поля запроса",
                    List.of(new FieldErrorDetail("password", "Пароль длиннее 72 байт в UTF-8"))
            );
        }
        UserAccount account = create(normalizeEmail(email), username.trim(), passwordEncoder.encode(password), null);
        // Письмо с подтверждением уходит только если регистрация зафиксирована: ставится в той же транзакции.
        emailConfirmation.send(account);
        return account;
    }

    /**
     * Аккаунт для нового пользователя Google. Email уже подтверждён провайдером, пароля нет.
     * Занятый email сюда не попадает: такой случай решает вызывающий код и не привязывает аккаунт автоматически.
     */
    @Transactional
    public UserAccount registerExternal(String email, String preferredUsername) {
        return create(normalizeEmail(email), uniqueUsername(preferredUsername), null, clock.instant());
    }

    private UserAccount create(String email, String username, String passwordHash, Instant verifiedAt) {
        if (users.findByEmailIgnoreCase(email).isPresent() || users.findByUsernameIgnoreCase(username).isPresent()) {
            throw ApiException.conflict(DUPLICATE_DETAIL);
        }
        Instant now = clock.instant();
        UUID userId = UUID.randomUUID();
        UserAccount account = new UserAccount(userId, email, username, passwordHash, now);
        if (verifiedAt != null) {
            account.markEmailVerified(verifiedAt);
        }
        try {
            // Флаш здесь, а не при коммите: нарушение уникальности должно стать 409, а не общей ошибкой.
            users.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(DUPLICATE_DETAIL);
        }
        profiles.save(new UserProfile(userId, username, now));
        roles.save(new UserRoleGrant(new UserRoleId(userId, "USER"), now));
        return account;
    }

    /** Из предпочтительного имени делает допустимый username; при занятости добавляет случайный суффикс. */
    private String uniqueUsername(String preferred) {
        String base = preferred.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.length() < 3) {
            base = "user_" + base;
        }
        base = base.length() > 22 ? base.substring(0, 22) : base;
        String candidate = base;
        for (int attempt = 0; attempt < 10; attempt++) {
            if (users.findByUsernameIgnoreCase(candidate).isEmpty()) {
                return candidate;
            }
            candidate = base + "_" + String.format(Locale.ROOT, "%04d", RANDOM.nextInt(10_000));
        }
        throw ApiException.conflict(DUPLICATE_DETAIL);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
