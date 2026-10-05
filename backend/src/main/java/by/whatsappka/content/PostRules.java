package by.whatsappka.content;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.util.List;

/** Правила полей публикации (FR-03): длина текста и предел вложений. */
final class PostRules {

    static final int BODY_MAX = 10000;
    static final int MEDIA_MAX = 10;

    private PostRules() {
    }

    /** Пустой текст допустим — черновик можно сохранить без содержимого, публикация проверит это отдельно. */
    static String normalizeBody(String raw) {
        String body = raw == null ? null : raw.strip();
        if (body != null && body.isEmpty()) {
            body = null;
        }
        if (body != null && body.length() > BODY_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("body", "Текст до 10000 символов")));
        }
        return body;
    }

    static void validateMedia(List<?> media) {
        if (media.size() > MEDIA_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("media", "Не более 10 изображений")));
        }
    }
}
