package by.whatsappka.notifications.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.notifications.NotificationQueries;
import by.whatsappka.notifications.NotificationRules;
import by.whatsappka.notifications.NotificationService;
import by.whatsappka.notifications.NotificationType;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Только собственные уведомления: всё, что касается чужих id, отвечает 404. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class NotificationController {

    private final NotificationService notifications;
    private final NotificationQueries queries;

    public NotificationController(NotificationService notifications, NotificationQueries queries) {
        this.notifications = notifications;
        this.queries = queries;
    }

    @GetMapping("/me/notifications")
    public CursorPage<NotificationQueries.NotificationView> list(
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return queries.list(viewer.userId(), cursor, limit);
    }

    @GetMapping("/me/notifications/unread-count")
    public UnreadCount unread(@AuthenticationPrincipal AuthenticatedUser viewer) {
        return new UnreadCount(notifications.unreadCount(viewer.userId()));
    }

    @PostMapping("/me/notifications/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        notifications.markRead(viewer.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/notifications/read-all")
    public UpdatedCount markAllRead(@AuthenticationPrincipal AuthenticatedUser viewer) {
        return new UpdatedCount(notifications.markAllRead(viewer.userId()));
    }

    @GetMapping("/me/notification-preferences")
    public List<PreferenceView> preferences(@AuthenticationPrincipal AuthenticatedUser viewer) {
        return notifications.preferences(viewer.userId()).stream().map(PreferenceView::of).toList();
    }

    /** Системные уведомления можно только оставить включёнными. */
    @PutMapping("/me/notification-preferences/{type}")
    public PreferenceView setPreference(
            @PathVariable("type") String type,
            @RequestBody PreferenceRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        NotificationType parsed = NotificationRules.parseType(type);
        return PreferenceView.of(notifications.setPreference(viewer.userId(), parsed, request.enabled()));
    }

    public record UnreadCount(long unread) {
    }

    public record UpdatedCount(int updated) {
    }

    public record PreferenceRequest(boolean enabled) {
    }

    public record PreferenceView(NotificationType type, boolean enabled, boolean canDisable) {
        static PreferenceView of(NotificationService.Preference preference) {
            return new PreferenceView(preference.type(), preference.enabled(), preference.canDisable());
        }
    }
}
