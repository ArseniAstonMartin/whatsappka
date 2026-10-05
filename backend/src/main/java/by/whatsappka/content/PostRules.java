package by.whatsappka.content;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Правила полей публикации (FR-03): длина текста, предел вложений, хештеги и границы расписания. */
final class PostRules {

    static final int BODY_MAX = 10000;
    static final int MEDIA_MAX = 10;
    static final int HASHTAG_MAX = 10;
    static final Duration SCHEDULE_MIN_AHEAD = Duration.ofMinutes(1);
    static final Duration SCHEDULE_MAX_AHEAD = Duration.ofDays(90);

    /** Буквы (с юникодом — для «і»/«ў» и другой кириллицы), цифры и подчёркивание, без решётки и пробелов. */
    private static final Pattern HASHTAG = Pattern.compile("^[\\p{L}\\p{N}_]{1,64}$", Pattern.UNICODE_CHARACTER_CLASS);

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

    /** До 10 тегов, повторы после нормализации схлопываются; порядок первого упоминания сохраняется. */
    static List<String> normalizeHashtags(List<String> raw) {
        List<String> source = raw == null ? List.of() : raw;
        if (source.size() > HASHTAG_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("hashtags", "Не более 10 хештегов")));
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : source) {
            normalized.add(normalizeHashtag(tag));
        }
        return List.copyOf(normalized);
    }

    static String normalizeHashtag(String raw) {
        String tag = raw == null ? "" : Normalizer.normalize(raw.strip(), Normalizer.Form.NFKC);
        if (tag.startsWith("#")) {
            tag = tag.substring(1);
        }
        tag = tag.toLowerCase(Locale.ROOT);
        if (!HASHTAG.matcher(tag).matches()) {
            throw ApiException.validation("Проверьте поля запроса", List.of(new FieldErrorDetail(
                    "hashtags", "Хештег: буквы, цифры и подчёркивание, до 64 символов")));
        }
        return tag;
    }
}
