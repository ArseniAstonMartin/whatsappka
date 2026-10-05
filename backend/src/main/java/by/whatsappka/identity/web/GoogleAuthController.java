package by.whatsappka.identity.web;

import by.whatsappka.identity.AuthenticatedSession;
import by.whatsappka.identity.IdentityProperties;
import by.whatsappka.identity.SessionService;
import by.whatsappka.identity.oauth.GoogleOAuthProperties;
import by.whatsappka.identity.oauth.GoogleSignInService;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.PublicApi;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Вход и привязка Google. Токены приложения никогда не попадают в адрес: refresh уходит в HttpOnly cookie,
 * пользователя возвращают в SPA по фиксированным путям из настроек.
 */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GoogleAuthController {

    static final String STATE_COOKIE = "whatsappka_oauth";
    static final String STATE_PATH = "/api/v1/auth/google";

    private final GoogleSignInService signIn;
    private final SessionService sessions;
    private final GoogleOAuthProperties google;
    private final RefreshCookies refreshCookies;
    private final boolean secure;

    public GoogleAuthController(
            GoogleSignInService signIn,
            SessionService sessions,
            GoogleOAuthProperties google,
            RefreshCookies refreshCookies,
            IdentityProperties identity
    ) {
        this.signIn = signIn;
        this.sessions = sessions;
        this.google = google;
        this.refreshCookies = refreshCookies;
        this.secure = identity.refreshCookieSecure();
    }

    @PublicApi
    @GetMapping("/auth/google/start")
    public ResponseEntity<Void> start() {
        GoogleSignInService.Start start = signIn.startLogin();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(start.authorizationUrl()))
                .header(HttpHeaders.SET_COOKIE, stateCookie(start.stateToken()).toString())
                .build();
    }

    /** Привязка требует действующего входа: возвращает адрес для перехода к Google. */
    @PostMapping("/auth/google/link")
    public ResponseEntity<GoogleLinkResponse> link(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        AuthenticatedSession current = sessions.authenticate(authorization);
        GoogleSignInService.Start start = signIn.startLink(current.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, stateCookie(start.stateToken()).toString())
                .body(new GoogleLinkResponse(start.authorizationUrl()));
    }

    @PublicApi
    @GetMapping("/auth/google/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @CookieValue(name = STATE_COOKIE, required = false) String stateToken
    ) {
        GoogleSignInService.Outcome outcome = signIn.complete(code, state, error, stateToken);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, clearStateCookie().toString());
        if (outcome instanceof GoogleSignInService.SignedIn signedIn) {
            return response
                    .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(signedIn.refreshToken(), signedIn.refreshExpiresAt()).toString())
                    .location(appUri("/feed", null))
                    .build();
        }
        if (outcome instanceof GoogleSignInService.Linked) {
            return response.location(appUri("/settings", "google=linked")).build();
        }
        GoogleSignInService.Failed failed = (GoogleSignInService.Failed) outcome;
        String path = failed.linkFlow() ? "/settings" : "/login";
        String query = "error=" + URLEncoder.encode(failed.code(), StandardCharsets.UTF_8);
        return response.location(appUri(path, query)).build();
    }

    private URI appUri(String path, String query) {
        String base = google.appUrl().endsWith("/")
                ? google.appUrl().substring(0, google.appUrl().length() - 1)
                : google.appUrl();
        return URI.create(base + path + (query == null ? "" : "?" + query));
    }

    private ResponseCookie stateCookie(String token) {
        return ResponseCookie.from(STATE_COOKIE, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(STATE_PATH)
                .maxAge(Duration.ofMinutes(10))
                .build();
    }

    private ResponseCookie clearStateCookie() {
        return ResponseCookie.from(STATE_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(STATE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    public record GoogleLinkResponse(String authorizationUrl) {
    }
}
