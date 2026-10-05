package by.whatsappka.content;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.util.List;

/** Правила комментариев (FR-05): длина тела и предел вложенности. */
final class CommentRules {

    static final int BODY_MAX = 2000;
    static final int MAX_DEPTH = 3;

    private CommentRules() {
    }

    static String normalizeBody(String raw) {
        String body = raw == null ? "" : raw.strip();
        if (body.isEmpty() || body.length() > BODY_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("body", "Комментарий от 1 до 2000 символов")));
        }
        return body;
    }
}
