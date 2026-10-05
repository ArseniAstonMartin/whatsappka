package by.whatsappka.identity.web;

import by.whatsappka.identity.recovery.EmailConfirmationService;
import by.whatsappka.identity.recovery.PasswordResetService;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.ratelimit.RateLimitPolicy;
import by.whatsappka.platform.ratelimit.RateLimiter;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Подтверждение почты и сброс пароля (TASK-016). Ответы на запросы восстановления не зависят от наличия адреса. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccountRecoveryController {

    private final PasswordResetService passwordResets;
    private final EmailConfirmationService emailConfirmations;
    private final RateLimiter limits;

    public AccountRecoveryController(
            PasswordResetService passwordResets,
            EmailConfirmationService emailConfirmations,
            RateLimiter limits
    ) {
        this.passwordResets = passwordResets;
        this.emailConfirmations = emailConfirmations;
        this.limits = limits;
    }

    @PublicApi
    @PostMapping("/auth/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest) {
        limits.consume("password-reset", httpRequest.getRemoteAddr(), 5, RateLimitPolicy.HOUR);
        passwordResets.request(request.email());
    }

    @PublicApi
    @PostMapping("/auth/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResets.reset(request.token(), request.password());
    }

    @PublicApi
    @PostMapping("/auth/email-confirmation/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmail(@Valid @RequestBody TokenRequest request) {
        emailConfirmations.confirm(request.token());
    }

    /** Повторная отправка письма с подтверждением. Для уже подтверждённого адреса ничего не отправляется. */
    @PostMapping("/me/email-confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendEmailConfirmation(@AuthenticationPrincipal AuthenticatedUser viewer) {
        limits.consume("email-confirmation", viewer.userId().toString(), 5, RateLimitPolicy.HOUR);
        emailConfirmations.resend(viewer.userId());
    }

    public record PasswordResetRequest(
            @NotBlank @Email @Size(max = 254) String email
    ) {
    }

    public record TokenRequest(
            @NotBlank @Size(max = 128) String token
    ) {
    }

    public record PasswordResetConfirmRequest(
            @NotBlank @Size(max = 128) String token,
            @NotNull @Size(min = 10, max = 72) String password
    ) {
    }
}
