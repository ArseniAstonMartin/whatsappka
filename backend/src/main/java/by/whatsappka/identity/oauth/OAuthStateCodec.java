package by.whatsappka.identity.oauth;

import by.whatsappka.identity.IdentityProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/**
 * Упаковывает состояние обращения в короткоживущую подписанную cookie. Сервер не хранит его между запросами.
 * Аудитория отличается от access JWT, поэтому токен одного назначения не принимается в другом.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OAuthStateCodec {

    static final String ISSUER = "whatsappka-oauth";
    static final String AUDIENCE = "google-oauth";
    public static final Duration TTL = Duration.ofMinutes(10);

    private final MACSigner signer;
    private final MACVerifier verifier;
    private final Clock clock;

    public OAuthStateCodec(IdentityProperties properties, Clock clock) {
        try {
            byte[] key = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException e) {
            throw new IllegalStateException("Секрет JWT короче 256 бит", e);
        }
        this.clock = clock;
    }

    public String seal(OAuthState state) {
        Instant now = clock.instant();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(TTL)))
                .claim("state", state.state())
                .claim("nonce", state.nonce())
                .claim("verifier", state.codeVerifier())
                .claim("mode", state.mode().name());
        if (state.userId() != null) {
            claims.claim("uid", state.userId().toString());
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Не удалось подписать состояние OAuth", e);
        }
        return jwt.serialize();
    }

    public Optional<OAuthState> open(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiry = claims.getExpirationTime();
            if (!ISSUER.equals(claims.getIssuer())
                    || claims.getAudience() == null
                    || !claims.getAudience().contains(AUDIENCE)
                    || expiry == null
                    || !clock.instant().isBefore(expiry.toInstant())) {
                return Optional.empty();
            }
            String state = claims.getStringClaim("state");
            String nonce = claims.getStringClaim("nonce");
            String codeVerifier = claims.getStringClaim("verifier");
            String mode = claims.getStringClaim("mode");
            String uid = claims.getStringClaim("uid");
            if (state == null || nonce == null || codeVerifier == null || mode == null) {
                return Optional.empty();
            }
            return Optional.of(new OAuthState(
                    state,
                    nonce,
                    codeVerifier,
                    OAuthState.Mode.valueOf(mode),
                    uid == null ? null : UUID.fromString(uid)
            ));
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
