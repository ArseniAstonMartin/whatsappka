package by.whatsappka.identity.oauth;

import by.whatsappka.identity.RegistrationService;
import by.whatsappka.identity.SessionService;
import by.whatsappka.identity.account.ExternalIdentity;
import by.whatsappka.identity.account.ExternalIdentityRepository;
import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.platform.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Решения о входе и привязке в одной транзакции. Совпадение email никогда не привязывает Google к чужому аккаунту:
 * для этого нужен вход в аккаунт и явная привязка.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GoogleAccountService {

    static final String DEVICE_LABEL = "Вход через Google";

    private final UserAccountRepository users;
    private final ExternalIdentityRepository identities;
    private final RegistrationService registration;
    private final SessionService sessions;
    private final Clock clock;

    public GoogleAccountService(
            UserAccountRepository users,
            ExternalIdentityRepository identities,
            RegistrationService registration,
            SessionService sessions,
            Clock clock
    ) {
        this.users = users;
        this.identities = identities;
        this.registration = registration;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public GoogleSignInService.Outcome link(UUID userId, GoogleIdentity identity) {
        UserAccount user = users.findById(userId)
                .filter(UserAccount::isActive)
                .orElseThrow(ApiException::unauthorized);
        Optional<ExternalIdentity> existing = identities.findByProviderAndSubject(ExternalIdentity.PROVIDER_GOOGLE, identity.subject());
        if (existing.isPresent()) {
            if (existing.get().userId().equals(user.id())) {
                return new GoogleSignInService.Linked();
            }
            throw new ApiException(HttpStatus.CONFLICT, "google_already_linked",
                    "Этот аккаунт Google уже привязан к другому пользователю", List.of(), null);
        }
        identities.save(new ExternalIdentity(UUID.randomUUID(), user.id(), ExternalIdentity.PROVIDER_GOOGLE,
                identity.subject(), clock.instant()));
        trustProviderEmail(user, identity, clock.instant());
        return new GoogleSignInService.Linked();
    }

    @Transactional
    public GoogleSignInService.Outcome signIn(GoogleIdentity identity) {
        Instant now = clock.instant();
        Optional<ExternalIdentity> linked = identities.findByProviderAndSubject(ExternalIdentity.PROVIDER_GOOGLE, identity.subject());
        UserAccount user;
        if (linked.isPresent()) {
            user = users.findById(linked.get().userId())
                    .orElseThrow(ApiException::unauthorized);
        } else {
            if (users.findByEmailIgnoreCase(identity.email()).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "email_in_use",
                        "Аккаунт с этим email уже есть. Войдите и привяжите Google в настройках", List.of(), null);
            }
            user = registration.registerExternal(identity.email(), localPart(identity.email()));
            identities.save(new ExternalIdentity(UUID.randomUUID(), user.id(), ExternalIdentity.PROVIDER_GOOGLE,
                    identity.subject(), now));
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "account_disabled", "Аккаунт отключён", List.of(), null);
        }
        trustProviderEmail(user, identity, now);
        user.recordActivity(now);
        SessionService.Opened opened = sessions.open(user, DEVICE_LABEL);
        return new GoogleSignInService.SignedIn(opened.refreshToken(), opened.refreshExpiresAt());
    }

    /** Google уже подтвердил адрес: если он совпадает с адресом аккаунта, повторное подтверждение не нужно. */
    private static void trustProviderEmail(UserAccount user, GoogleIdentity identity, Instant now) {
        if (identity.emailVerified() && user.email().equalsIgnoreCase(identity.email())) {
            user.markEmailVerified(now);
        }
    }

    private static String localPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
