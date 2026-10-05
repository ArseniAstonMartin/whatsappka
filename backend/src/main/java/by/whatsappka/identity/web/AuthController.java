package by.whatsappka.identity.web;

import by.whatsappka.identity.AuthenticationService;
import by.whatsappka.identity.RegistrationService;
import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.token.AccessTokenIssuer;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final RefreshCookies refreshCookies;

    public AuthController(
            RegistrationService registration,
            AuthenticationService authentication,
            RefreshCookies refreshCookies
    ) {
        this.registration = registration;
        this.authentication = authentication;
        this.refreshCookies = refreshCookies;
    }

    @PublicApi
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary register(@Valid @RequestBody RegisterRequest request) {
        UserAccount account = registration.register(request.email(), request.username(), request.password());
        return new UserSummary(account.id(), account.username());
    }

    @PublicApi
    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticationService.LoginResult result = authentication.login(
                request.email(),
                request.password(),
                deviceLabel(httpRequest)
        );
        UserAccount user = result.user();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(new LoginResponse(
                        result.accessToken(),
                        "Bearer",
                        AccessTokenIssuer.LIFETIME.toSeconds(),
                        new UserSummary(user.id(), user.username())
                ));
    }

    private static String deviceLabel(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN_DEVICE;
        }
        return userAgent.length() > DEVICE_LABEL_MAX ? userAgent.substring(0, DEVICE_LABEL_MAX) : userAgent;
    }
}
