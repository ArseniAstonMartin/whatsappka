package by.whatsappka.identity.oauth;

import by.whatsappka.identity.IdentityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/** Обращения к Google: discovery, обмен кода на токены и проверка ID token. Секрет клиента уходит только сюда. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GoogleOidcClient {

    static final URI DISCOVERY = URI.create("https://accounts.google.com/.well-known/openid-configuration");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final GoogleOAuthProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private volatile Endpoints endpoints;
    private volatile GoogleIdTokenValidator validator;

    public GoogleOidcClient(GoogleOAuthProperties properties, ObjectMapper mapper, Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    public String authorizationUrl(OAuthState state) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("response_type", "code");
        query.put("client_id", properties.clientId());
        query.put("redirect_uri", properties.redirectUri());
        query.put("scope", "openid email profile");
        query.put("state", state.state());
        query.put("nonce", state.nonce());
        query.put("code_challenge", challenge(state.codeVerifier()));
        query.put("code_challenge_method", "S256");
        query.put("prompt", "select_account");
        return endpoints().authorization() + "?" + form(query);
    }

    /** Возвращает id_token из ответа токен-эндпоинта. */
    public String exchangeCode(String code, String codeVerifier) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", properties.redirectUri());
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("code_verifier", codeVerifier);
        JsonNode json = send(HttpRequest.newBuilder(URI.create(endpoints().token()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(body)))
                .build());
        JsonNode idToken = json.get("id_token");
        if (idToken == null || !idToken.isTextual()) {
            throw new GoogleUnavailableException();
        }
        return idToken.asText();
    }

    public GoogleIdentity verifyIdToken(String idToken, String nonce) {
        return validator().validate(idToken, nonce);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new GoogleUnavailableException();
            }
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new GoogleUnavailableException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleUnavailableException();
        }
    }

    private Endpoints endpoints() {
        Endpoints current = endpoints;
        if (current != null) {
            return current;
        }
        JsonNode json = send(HttpRequest.newBuilder(DISCOVERY).timeout(TIMEOUT).GET().build());
        Endpoints fetched = new Endpoints(
                text(json, "authorization_endpoint"),
                text(json, "token_endpoint"),
                text(json, "jwks_uri")
        );
        endpoints = fetched;
        return fetched;
    }

    private GoogleIdTokenValidator validator() {
        GoogleIdTokenValidator current = validator;
        if (current != null) {
            return current;
        }
        try {
            RemoteJWKSet<SecurityContext> keys = new RemoteJWKSet<>(URI.create(endpoints().jwks()).toURL());
            GoogleIdTokenValidator created = new GoogleIdTokenValidator(properties.clientId(), keys, clock);
            validator = created;
            return created;
        } catch (java.net.MalformedURLException e) {
            throw new GoogleUnavailableException();
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isTextual()) {
            throw new GoogleUnavailableException();
        }
        return value.asText();
    }

    /** PKCE: challenge = BASE64URL(SHA-256(verifier)). */
    static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    private static String form(Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        values.forEach((key, value) -> {
            if (!out.isEmpty()) {
                out.append('&');
            }
            out.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return out.toString();
    }

    private record Endpoints(String authorization, String token, String jwks) {
    }
}
