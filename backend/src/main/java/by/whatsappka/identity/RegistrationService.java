package by.whatsappka.identity;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.identity.account.UserProfile;
import by.whatsappka.identity.account.UserProfileRepository;
import by.whatsappka.identity.account.UserRoleGrant;
import by.whatsappka.identity.account.UserRoleId;
import by.whatsappka.identity.account.UserRoleRepository;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.nio.charset.StandardCharsets;
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

/** Регистрация: пользователь, профиль и роль USER создаются одной транзакцией. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RegistrationService {

    static final int PASSWORD_MAX_BYTES = 72;
    private static final String DUPLICATE_DETAIL = "Не удалось зарегистрировать аккаунт с такими данными";

    private final UserAccountRepository users;
    private final UserProfileRepository profiles;
    private final UserRoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegistrationService(
            UserAccountRepository users,
            UserProfileRepository profiles,
            UserRoleRepository roles,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.users = users;
        this.profiles = profiles;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
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
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String trimmedUsername = username.trim();
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()
                || users.findByUsernameIgnoreCase(trimmedUsername).isPresent()) {
            throw ApiException.conflict(DUPLICATE_DETAIL);
        }

        Instant now = clock.instant();
        UUID userId = UUID.randomUUID();
        UserAccount account = new UserAccount(
                userId,
                normalizedEmail,
                trimmedUsername,
                passwordEncoder.encode(password),
                now
        );
        try {
            // Флаш здесь, а не при коммите: нарушение уникальности должно стать 409, а не общей ошибкой.
            users.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(DUPLICATE_DETAIL);
        }
        profiles.save(new UserProfile(userId, trimmedUsername, now));
        roles.save(new UserRoleGrant(new UserRoleId(userId, "USER"), now));
        return account;
    }
}
