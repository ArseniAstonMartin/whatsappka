package by.whatsappka.profiles;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.identity.account.UserProfile;
import by.whatsappka.identity.account.UserProfileRepository;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProfileService {

    private final UserAccountRepository users;
    private final UserProfileRepository profiles;
    private final Clock clock;

    public ProfileService(UserAccountRepository users, UserProfileRepository profiles, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicProfile publicProfile(String username) {
        UserAccount account = users.findByUsernameIgnoreCase(username)
                .filter(UserAccount::isActive)
                .orElseThrow(ApiException::notFound);
        UserProfile profile = profiles.findById(account.id()).orElseThrow(ApiException::notFound);
        return new PublicProfile(
                account.id(),
                account.username(),
                profile.displayName(),
                profile.bio(),
                profile.statusText(),
                profile.isVerified(),
                profile.avatarMediaId(),
                profile.coverMediaId());
    }

    @Transactional(readOnly = true)
    public OwnProfile ownProfile(UUID userId) {
        UserAccount account = activeAccount(userId);
        UserProfile profile = profiles.findById(userId).orElseThrow(ApiException::notFound);
        return own(account, profile);
    }

    @Transactional
    public OwnProfile update(UUID userId, ProfilePatch patch) {
        UserAccount account = activeAccount(userId);
        UserProfile profile = profiles.findById(userId).orElseThrow(ApiException::notFound);
        String timezone = patch.timezone() == null ? null : validTimezone(patch.timezone());
        String displayName = patch.displayName() == null ? null : requireNotBlank(patch.displayName());
        profile.update(displayName, patch.bio(), patch.statusText(), timezone, clock.instant());
        return own(account, profile);
    }

    private UserAccount activeAccount(UUID userId) {
        return users.findById(userId).filter(UserAccount::isActive).orElseThrow(ApiException::unauthorized);
    }

    private static OwnProfile own(UserAccount account, UserProfile profile) {
        return new OwnProfile(
                account.id(),
                account.username(),
                account.email(),
                profile.displayName(),
                profile.bio(),
                profile.statusText(),
                profile.timezone(),
                profile.isVerified(),
                profile.avatarMediaId(),
                profile.coverMediaId());
    }

    private static String requireNotBlank(String value) {
        if (value.isBlank()) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("displayName", "Обязательное поле")));
        }
        return value.trim();
    }

    /** Часовой пояс — идентификатор региона из списка Java (например, Europe/Minsk). Смещения вида +03:00 не принимаются. */
    static String validTimezone(String value) {
        String normalized = value.trim();
        if (!ZoneId.getAvailableZoneIds().contains(normalized) || normalized.startsWith("SystemV/")) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("timezone", "Неизвестный часовой пояс")));
        }
        return normalized;
    }
}
