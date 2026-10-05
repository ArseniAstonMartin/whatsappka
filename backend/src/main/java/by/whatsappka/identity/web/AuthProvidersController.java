package by.whatsappka.identity.web;

import by.whatsappka.identity.oauth.GoogleOAuthProperties;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.PublicApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;

/** Какие способы входа настроены на сервере. Интерфейс не показывает способ, которого нет. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthProvidersController {

    private final GoogleOAuthProperties google;

    public AuthProvidersController(GoogleOAuthProperties google) {
        this.google = google;
    }

    @PublicApi
    @GetMapping("/auth/providers")
    public AuthProviders providers() {
        return new AuthProviders(google.configured());
    }

    public record AuthProviders(boolean google) {
    }
}
