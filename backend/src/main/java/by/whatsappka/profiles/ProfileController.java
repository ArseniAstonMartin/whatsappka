package by.whatsappka.profiles;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/** Публичный профиль читают авторизованные пользователи; собственный профиль меняет только сам пользователь. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/users/{username}")
    public PublicProfile publicProfile(@PathVariable("username") String username) {
        return profiles.publicProfile(username);
    }

    @GetMapping("/me/profile")
    public OwnProfile ownProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return profiles.ownProfile(user.userId());
    }

    @PatchMapping("/me/profile")
    public OwnProfile update(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ProfilePatch patch) {
        return profiles.update(user.userId(), patch);
    }
}
