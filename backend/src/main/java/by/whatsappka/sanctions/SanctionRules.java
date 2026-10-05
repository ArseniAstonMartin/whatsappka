package by.whatsappka.sanctions;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.util.List;

/**
 * Кто кого может ограничить. Модератор — только временно и не модераторов и администраторов.
 * Администратора не ограничивает никто. Постоянная блокировка и снятие — только администратор.
 */
public final class SanctionRules {

    public enum Kind { TEMPORARY, PERMANENT }

    public static final int DURATION_MIN_HOURS = 1;
    public static final int DURATION_MAX_HOURS = 720;

    private SanctionRules() {
    }

    public static Kind parseKind(String raw) {
        try {
            return Kind.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("kind", "Допустимы TEMPORARY или PERMANENT")));
        }
    }

    public static void requireDuration(Kind kind, Integer hours) {
        if (kind == Kind.PERMANENT) {
            if (hours != null) {
                throw ApiException.validation("Проверьте поля запроса",
                        List.of(new FieldErrorDetail("durationHours", "Постоянная блокировка не имеет срока")));
            }
            return;
        }
        if (hours == null || hours < DURATION_MIN_HOURS || hours > DURATION_MAX_HOURS) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("durationHours", "Срок от 1 до 720 часов")));
        }
    }

    /** Проверка прав до любых изменений. Роли цели читаются на этом же запросе. */
    public static void requireMayIssue(boolean actorIsAdmin, Kind kind, List<String> targetRoles) {
        if (targetRoles.contains("ADMIN")) {
            throw forbidden();
        }
        if (!actorIsAdmin) {
            if (kind != Kind.TEMPORARY || targetRoles.contains("MODERATOR")) {
                throw forbidden();
            }
        }
    }

    public static ApiException forbidden() {
        return new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "forbidden",
                "Это действие вам недоступно", List.of(), null);
    }
}
