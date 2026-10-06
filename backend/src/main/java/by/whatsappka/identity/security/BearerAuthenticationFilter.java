package by.whatsappka.identity.security;

import by.whatsappka.platform.web.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import by.whatsappka.identity.activity.ActivityRecorder;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Устанавливает принципала по Bearer-токену. Неверный токен не прерывает цепочку:
 * запрос остаётся анонимным, и защищённые маршруты отвечают 401 через обработчик безопасности.
 */
public class BearerAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(BearerAuthenticationFilter.class);

    private final AccessService access;
    private final ActivityRecorder activity;

    public BearerAuthenticationFilter(AccessService access, ActivityRecorder activity) {
        this.access = access;
        this.activity = activity;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            try {
                AuthenticatedUser user = access.authenticate(header);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.authorities())
                );
                recordActivity(user);
            } catch (ApiException rejected) {
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Сбой учёта не должен ломать запрос: DAU тогда будет чуть меньше, но сервис работает. */
    private void recordActivity(AuthenticatedUser user) {
        try {
            activity.recordAuthenticated(user.userId());
        } catch (RuntimeException e) {
            log.warn("Активность не записана: {}", e.getClass().getSimpleName());
        }
    }
}
