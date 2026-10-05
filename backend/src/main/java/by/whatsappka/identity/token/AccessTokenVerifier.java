package by.whatsappka.identity.token;

import by.whatsappka.identity.AuthenticatedSession;
import by.whatsappka.identity.IdentityProperties;
import by.whatsappka.platform.web.ApiException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/** Проверяет подпись, алгоритм, issuer, audience и срок access JWT. Состояние сессии проверяет вызывающий сервис. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccessTokenVerifier {

    private final MACVerifier verifier;
    private final Clock clock;

    public AccessTokenVerifier(IdentityProperties properties, Clock clock) {
        try {
            this.verifier = new MACVerifier(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        } catch (JOSEException e) {
            throw new IllegalStateException("Секрет JWT короче 256 бит", e);
        }
        this.clock = clock;
    }

    public AuthenticatedSession verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                throw ApiException.unauthorized();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiry = claims.getExpirationTime();
            if (!AccessTokenIssuer.ISSUER.equals(claims.getIssuer())
                    || claims.getAudience() == null
                    || !claims.getAudience().contains(AccessTokenIssuer.AUDIENCE)
                    || expiry == null
                    || !clock.instant().isBefore(expiry.toInstant())
                    || claims.getSubject() == null
                    || claims.getStringClaim("sid") == null) {
                throw ApiException.unauthorized();
            }
            return new AuthenticatedSession(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.getStringClaim("sid"))
            );
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            throw ApiException.unauthorized();
        }
    }
}
