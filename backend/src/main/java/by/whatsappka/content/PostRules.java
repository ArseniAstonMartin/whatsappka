package by.whatsappka.content;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Правила полей публикации (FR-03): длина текста, предел вложений и границы расписания. */
final class PostRules {

    static final int BODY_MAX = 10000;
    static final int MEDIA_MAX = 10;
    static final Duration SCHEDULE_MIN_AHEAD = Duration.ofMinutes(1);
    static final Duration SCHEDULE_MAX_AHEAD = Duration.ofDays(90);

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

    /** Расписание принимает время от 1 минуты до 90 дней вперёд (PRD FR-03). */
    static void validateScheduleTime(Instant publishAt, Instant now) {
        if (publishAt == null || publishAt.isBefore(now.plus(SCHEDULE_MIN_AHEAD)) || publishAt.isAfter(now.plus(SCHEDULE_MAX_AHEAD))) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("publishAt", "Время от 1 минуты до 90 дней вперёд")));
        }
    }
}
