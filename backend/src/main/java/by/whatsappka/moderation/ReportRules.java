package by.whatsappka.moderation;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.util.List;

/** Причины и описание жалобы. Причина строгая, описание до 2000 символов. */
public final class ReportRules {

    public static final int DESCRIPTION_MAX = 2000;

    public enum Reason { SPAM, HARASSMENT, HATE, VIOLENCE, SEXUAL, SELF_HARM, IMPERSONATION, OTHER }

    public enum TargetKind { USER, POST, COMMENT, MESSAGE, GROUP }

    private ReportRules() {
    }

    public static Reason parseReason(String raw) {
        try {
            return Reason.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("reason", "Выберите причину из списка")));
        }
    }

    public static TargetKind parseKind(String raw) {
        try {
            return TargetKind.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("targetKind", "Неизвестный вид объекта")));
        }
    }

    /** Пустое описание — null; иначе обрезанный текст до 2000 символов. */
    public static String normalizeDescription(String raw) {
        String text = raw == null ? null : raw.strip();
        if (text != null && text.isEmpty()) {
            return null;
        }
        if (text != null && text.length() > DESCRIPTION_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("description", "Не больше 2000 символов")));
        }
        return text;
    }
}
