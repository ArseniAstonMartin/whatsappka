package by.whatsappka.identity.web;

import by.whatsappka.identity.AuthenticatedSession;
import by.whatsappka.identity.SessionService;
import by.whatsappka.identity.session.AuthSession;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** Операции над сессиями текущего пользователя; авторизация только через Bearer access JWT. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SessionController {

    private final SessionService sessions;
    private final RefreshCookies refreshCookies;

    public SessionController(SessionService sessions, RefreshCookies refreshCookies) {
        this.sessions = sessions;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/auth/logout-all")
    public ResponseEntity<Void> logoutAll(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        AuthenticatedSession current = sessions.authenticate(authorization);
        sessions.revokeAll(current.userId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    @GetMapping("/auth/sessions")
    public CursorPage<SessionView> list(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        AuthenticatedSession current = sessions.authenticate(authorization);
        // Активные сессии ограничены сроком 30 дней; постраничность не нужна, поле nextCursor всегда пустое.
        List<SessionView> items = sessions.listActive(current.userId()).stream()
                .map(session -> toView(session, current.sessionId()))
                .toList();
        return new CursorPage<>(items, null, false);
    }

    @DeleteMapping("/auth/sessions/{id}")
    public ResponseEntity<Void> revoke(
            @PathVariable("id") UUID id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        AuthenticatedSession current = sessions.authenticate(authorization);
        sessions.revoke(current.userId(), id);
        return ResponseEntity.noContent().build();
    }

    private static SessionView toView(AuthSession session, UUID currentSessionId) {
        return new SessionView(
                session.id(),
                session.deviceLabel(),
                session.createdAt(),
                session.lastUsedAt(),
                session.id().equals(currentSessionId)
        );
    }
}
