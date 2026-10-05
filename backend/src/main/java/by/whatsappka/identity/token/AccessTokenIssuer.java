package by.whatsappka.identity.token;

import by.whatsappka.identity.IdentityProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/** Выпускает access JWT HS256 на 15 минут; проверка входящих токенов относится к отдельной задаче. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccessTokenIssuer {

    public static final Duration LIFETIME = Duration.ofMinutes(15);
    public static final String ISSUER = "whatsappka";
    public static final String AUDIENCE = "whatsappka-api";

    private final MACSigner signer;
    private final Clock clock;

    public AccessTokenIssuer(IdentityProperties properties, Clock clock) {
        try {
            this.signer = new MACSigner(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        } catch (KeyLengthException e) {
            throw new IllegalStateException("Секрет JWT короче 256 бит", e);
        }
        this.clock = clock;
    }

    public String issue(UUID userId, UUID sessionId) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(LIFETIME)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Не удалось подписать access JWT", e);
        }
        return jwt.serialize();
    }
}
