package by.whatsappka.identity.web;

import by.whatsappka.identity.IdentityProperties;
import by.whatsappka.platform.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Защита cookie-операций от CSRF: SameSite=Lax и обязательный Origin из allowlist.
 * Операции с Bearer-токеном в заголовке cookie не используют и этой проверки не требуют.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OriginGuard {

    private final Set<String> allowed;

    public OriginGuard(IdentityProperties properties) {
        this.allowed = properties.allowedOrigins().stream()
                .map(OriginGuard::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void require(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !allowed.contains(normalize(origin))) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "forbidden_origin",
                    "Запрос с этого источника не разрешён",
                    List.of(),
                    null
            );
        }
    }

    private static String normalize(String origin) {
        String trimmed = origin.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
