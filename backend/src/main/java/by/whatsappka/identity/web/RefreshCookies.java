package by.whatsappka.identity.web;

import by.whatsappka.identity.IdentityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Refresh-токен в HttpOnly cookie: SameSite=Lax и путь только для auth-операций. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RefreshCookies {

    public static final String NAME = "whatsappka_refresh";
    public static final String PATH = "/api/v1/auth";

    private final boolean secure;
    private final Clock clock;

    public RefreshCookies(IdentityProperties properties, Clock clock) {
        this.secure = properties.refreshCookieSecure();
        this.clock = clock;
    }

    public ResponseCookie issue(String refreshToken, Instant expiresAt) {
        return base(refreshToken)
                .maxAge(Duration.between(clock.instant(), expiresAt))
                .build();
    }

    /** Очистка cookie: тот же путь и атрибуты, Max-Age=0. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH);
    }
}
