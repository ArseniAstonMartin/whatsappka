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

    /**
     * Проверка цели до записи; повторяет ограничения схемы V25/V26. Нарушение — ошибка вызывающего кода.
     * Системное не ссылается ни на что. Подписка — на самого получателя и требует актора. Комментарий, ответ
     * и реакция требуют актора. Приглашения и заявки актора не требуют.
     */
    public static void requireValidTarget(UUID recipient, NotificationType type, UUID actor,
                                          NotificationType.TargetKind kind, UUID targetId) {
        boolean valid = switch (type) {
            case SYSTEM, MODERATION_RESULT, CONTENT_HIDDEN -> kind == null && targetId == null && actor == null;
            case FOLLOW -> type.allowsTarget(kind) && recipient.equals(targetId) && actor != null;
            case COMMENT, REPLY, REACTION, MESSAGE -> type.allowsTarget(kind) && targetId != null && actor != null;
            default -> type.allowsTarget(kind) && targetId != null;
        };
        if (!valid) {
            throw new IllegalArgumentException("invalid notification target for " + type + ": " + kind);
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
