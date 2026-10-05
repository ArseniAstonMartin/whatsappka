package by.whatsappka.identity.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * Проверяет ID token Google: подпись RS256 по JWKS, issuer, audience, срок, nonce, subject и подтверждённый email.
 * Не ходит в сеть сам: ключи приходят из источника, поэтому проверку можно тестировать отдельно.
 */
public class GoogleIdTokenValidator {

    static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final String clientId;
    private final JWKSource<SecurityContext> keys;
    private final Clock clock;

    public GoogleIdTokenValidator(String clientId, JWKSource<SecurityContext> keys, Clock clock) {
        this.clientId = clientId;
        this.keys = keys;
        this.clock = clock;
    }

    public GoogleIdentity validate(String idToken, String expectedNonce) {
        try {
            SignedJWT jwt = SignedJWT.parse(idToken);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new IdTokenRejectedException();
            }
            DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
            // Утверждения проверяются ниже явно, чтобы каждая причина отказа была видна в коде.
            processor.setJWTClaimsSetVerifier((claims, context) -> { });
            JWTClaimsSet claims = processor.process(jwt, null);
            return check(claims, expectedNonce);
        } catch (ParseException | BadJOSEException | JOSEException e) {
            throw new IdTokenRejectedException();
        }
    }

    private GoogleIdentity check(JWTClaimsSet claims, String expectedNonce) throws ParseException {
        Instant now = clock.instant();
        Date expiry = claims.getExpirationTime();
        if (!ISSUERS.contains(claims.getIssuer())
                || claims.getAudience() == null
                || !claims.getAudience().contains(clientId)
                || expiry == null
                || !now.isBefore(expiry.toInstant().plus(CLOCK_SKEW))) {
            throw new IdTokenRejectedException();
        }
        String nonce = claims.getStringClaim("nonce");
        if (expectedNonce == null || nonce == null
                || !MessageDigest.isEqual(expectedNonce.getBytes(StandardCharsets.UTF_8), nonce.getBytes(StandardCharsets.UTF_8))) {
            throw new IdTokenRejectedException();
        }
        String subject = claims.getSubject();
        String email = claims.getStringClaim("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new IdTokenRejectedException();
        }
        Boolean verified = claims.getBooleanClaim("email_verified");
        return new GoogleIdentity(subject, email.trim().toLowerCase(Locale.ROOT), Boolean.TRUE.equals(verified));
    }
}
