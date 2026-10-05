package by.whatsappka.notifications;

import by.whatsappka.platform.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Правила уведомлений: нельзя уведомлять себя, системные нельзя отключить, тип разбирается строго. */
public final class NotificationRules {

    private NotificationRules() {
    }

    /** Действия самого получателя уведомлений не порождают. */
    public static boolean isSelf(UUID recipient, UUID actor) {
        return actor != null && actor.equals(recipient);
    }

    public static NotificationType parseType(String raw) {
        try {
            return NotificationType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unknown_notification_type",
                    "Неизвестный тип уведомления", List.of(), null);
        }
    }

    /** Системные уведомления можно только оставить включёнными. */
    public static void requireCanSet(NotificationType type, boolean enabled) {
        if (!enabled && !type.canDisable()) {
            throw new ApiException(HttpStatus.CONFLICT, "system_notification_required",
                    "Системные уведомления отключить нельзя", List.of(), null);
        }
    }
}
