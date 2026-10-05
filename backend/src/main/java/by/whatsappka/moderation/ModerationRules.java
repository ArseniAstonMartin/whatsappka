package by.whatsappka.moderation;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.util.List;

/** Правила решений модератора: допустимые статусы, виды решений, причина обязательна для всех решений, кроме взятия в работу. */
public final class ModerationRules {

    public enum Status { OPEN, IN_REVIEW, RESOLVED, REJECTED }

    public enum Decision { HIDE, NO_ACTION }

    public static final int REASON_MAX = 2000;

    private ModerationRules() {
    }

    /** Фильтр очереди; без значения — открытые жалобы. */
    public static Status parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return Status.OPEN;
        }
        try {
            return Status.valueOf(raw.strip());
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("status", "Допустимы OPEN, IN_REVIEW, RESOLVED, REJECTED")));
        }
    }

    public static Decision parseDecision(String raw) {
        try {
            return Decision.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("action", "Допустимы HIDE или NO_ACTION")));
        }
    }

    /** Скрыть можно только то, что модерация умеет скрывать: пост, комментарий или сообщество. */
    public static boolean canHide(ReportRules.TargetKind kind) {
        return kind == ReportRules.TargetKind.POST || kind == ReportRules.TargetKind.COMMENT
                || kind == ReportRules.TargetKind.GROUP;
    }

    public static String requireReason(String raw) {
        String reason = raw == null ? "" : raw.strip();
        if (reason.isEmpty() || reason.length() > REASON_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("reason", "Причина от 1 до 2000 символов")));
        }
        return reason;
    }
}
