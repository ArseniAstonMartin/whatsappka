package by.whatsappka.identity.oauth;

import by.whatsappka.platform.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Code flow Google: старт выдаёт адрес и подписанную cookie состояния, callback обменивает код, проверяет ID token
 * и передаёт решение о входе или привязке в {@link GoogleAccountService}. Сетевые вызовы идут вне транзакции.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GoogleSignInService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleOAuthProperties properties;
    private final GoogleOidcClient oidc;
    private final OAuthStateCodec codec;
    private final GoogleAccountService accounts;

    public GoogleSignInService(
            GoogleOAuthProperties properties,
            GoogleOidcClient oidc,
            OAuthStateCodec codec,
            GoogleAccountService accounts
    ) {
        this.properties = properties;
        this.oidc = oidc;
        this.codec = codec;
        this.accounts = accounts;
    }

    public Start startLogin() {
        return start(OAuthState.Mode.LOGIN, null);
    }

    public Start startLink(UUID userId) {
        return start(OAuthState.Mode.LINK, userId);
    }

    private Start start(OAuthState.Mode mode, UUID userId) {
        if (!properties.configured()) {
            throw notConfigured();
        }
        OAuthState state = new OAuthState(randomToken(), randomToken(), randomToken(), mode, userId);
        try {
            return new Start(oidc.authorizationUrl(state), codec.seal(state));
        } catch (GoogleUnavailableException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "google_unavailable", "Google сейчас недоступен", List.of(), null);
        }
    }

    public Outcome complete(String code, String state, String error, String stateToken) {
        if (!properties.configured()) {
            return new Failed("google_not_configured", false);
        }
        Optional<OAuthState> opened = codec.open(stateToken);
        boolean linkFlow = opened.map(s -> s.mode() == OAuthState.Mode.LINK).orElse(false);
        if (error != null) {
            return new Failed("google_cancelled", linkFlow);
        }
        if (opened.isEmpty() || state == null
                || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
                        opened.get().state().getBytes(StandardCharsets.UTF_8))) {
            return new Failed("google_state_invalid", linkFlow);
        }
        if (code == null || code.isBlank()) {
            return new Failed("google_code_missing", linkFlow);
        }
        OAuthState flow = opened.get();
        GoogleIdentity identity;
        try {
            identity = oidc.verifyIdToken(oidc.exchangeCode(code, flow.codeVerifier()), flow.nonce());
        } catch (GoogleUnavailableException e) {
            return new Failed("google_unavailable", linkFlow);
        } catch (IdTokenRejectedException e) {
            return new Failed("google_token_invalid", linkFlow);
        }
        if (!identity.emailVerified()) {
            return new Failed("google_email_unverified", linkFlow);
        }
        try {
            return flow.mode() == OAuthState.Mode.LINK
                    ? accounts.link(flow.userId(), identity)
                    : accounts.signIn(identity);
        } catch (ApiException e) {
            return new Failed(e.code(), linkFlow);
        } catch (DataIntegrityViolationException e) {
            // Параллельный вход с тем же аккаунтом Google: уникальность (provider, subject) сработала.
            return new Failed("google_already_linked", linkFlow);
        }
    }

    private ApiException notConfigured() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "google_not_configured",
                "Вход через Google не настроен на сервере", List.of(), null);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Start(String authorizationUrl, String stateToken) {
    }

    public sealed interface Outcome permits SignedIn, Linked, Failed {
    }

    public record SignedIn(String refreshToken, Instant refreshExpiresAt) implements Outcome {
    }

    public record Linked() implements Outcome {
    }

    /** linkFlow: сбой относился к привязке, а не ко входу. От этого зависит, куда вернуть пользователя. */
    public record Failed(String code, boolean linkFlow) implements Outcome {
    }
}
