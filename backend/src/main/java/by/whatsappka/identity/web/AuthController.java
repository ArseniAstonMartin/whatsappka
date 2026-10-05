package by.whatsappka.identity.web;

import by.whatsappka.identity.AuthenticationService;
import by.whatsappka.identity.RegistrationService;
import by.whatsappka.identity.SessionService;
import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.token.AccessTokenIssuer;
import by.whatsappka.platform.ratelimit.RateLimiter;
import by.whatsappka.platform.ratelimit.RateLimitPolicy;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthController {

    private static final String UNKNOWN_DEVICE = "Неизвестное устройство";
    private static final int DEVICE_LABEL_MAX = 150;

    private final RegistrationService registration;
    private final AuthenticationService authentication;
    private final SessionService sessions;
    private final RefreshCookies refreshCookies;
    private final OriginGuard originGuard;

    private final RateLimiter limits;

    public AuthController(
            RegistrationService registration,
            AuthenticationService authentication,
            SessionService sessions,
            RefreshCookies refreshCookies,
            OriginGuard originGuard,
            RateLimiter limits
    ) {
        this.limits = limits;
        this.registration = registration;
        this.authentication = authentication;
        this.sessions = sessions;
        this.refreshCookies = refreshCookies;
        this.originGuard = originGuard;
    }

    @PublicApi
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        // PRD: не более 5 регистраций в час с одного адреса.
        limits.consume("register", httpRequest.getRemoteAddr(), 5, RateLimitPolicy.HOUR);
        UserAccount account = registration.register(request.email(), request.username(), request.password());
        return new UserSummary(account.id(), account.username());
    }

    @PublicApi
    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        // PRD: 5 неудачных попыток в минуту на сочетание адреса и почты. Засчитываются только неверные пароли;
        // ответ одинаков, существует аккаунт или нет, и окно скользит сразу, без постоянной блокировки.
        String loginKey = RateLimitPolicy.loginKey(httpRequest.getRemoteAddr(), request.email());
        limits.requireAvailable("login", loginKey, 5);
        AuthenticationService.LoginResult result;
        try {
            result = authentication.login(request.email(), request.password(), deviceLabel(httpRequest));
        } catch (ApiException failure) {
            if ("invalid_credentials".equals(failure.code())) {
                limits.recordFailure("login", loginKey, RateLimitPolicy.MINUTE);
            }
            throw failure;
        }
        UserAccount user = result.user();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(tokenResponse(result.accessToken(), new UserSummary(user.id(), user.username())));
    }

    @PublicApi
    @PostMapping("/auth/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        originGuard.require(httpRequest);
        SessionService.Rotation rotation = sessions.refresh(refreshToken);
        UserAccount user = rotation.user();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(rotation.refreshToken(), rotation.refreshExpiresAt()).toString())
                .body(tokenResponse(rotation.accessToken(), new UserSummary(user.id(), user.username())));
    }

    @PublicApi
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        originGuard.require(httpRequest);
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessions.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    private static LoginResponse tokenResponse(String accessToken, UserSummary user) {
        return new LoginResponse(accessToken, "Bearer", AccessTokenIssuer.LIFETIME.toSeconds(), user);
    }

    private static String deviceLabel(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN_DEVICE;
        }
        return userAgent.length() > DEVICE_LABEL_MAX ? userAgent.substring(0, DEVICE_LABEL_MAX) : userAgent;
    }
}
