package by.whatsappka.settings;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Проверка значений настроек. Границы загрузки и квоты не могут выйти за технические пределы: администратор
 * только снижает лимиты, поднять выше инфраструктуры нельзя.
 */
public final class AppSettingsRules {

    public static final long MIB = 1024L * 1024L;
    public static final long UPLOAD_MIN_BYTES = MIB;
    public static final long QUOTA_MIN_BYTES = 10 * MIB;
    public static final int SITE_NAME_MAX = 60;
    public static final int REASON_MAX = 500;

    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private AppSettingsRules() {
    }

    /** Возвращает значение в каноническом виде, которое пишется в таблицу. */
    public static String normalize(AppSettingKey key, String raw, long uploadCeiling, long quotaCeiling) {
        String value = raw == null ? "" : raw.trim();
        return switch (key) {
            case REGISTRATION_OPEN -> {
                if (!value.equals("true") && !value.equals("false")) {
                    throw invalid(key, "Укажите true или false");
                }
                yield value;
            }
            case UPLOAD_LIMIT_BYTES -> bytesInRange(key, value, UPLOAD_MIN_BYTES, uploadCeiling);
            case USER_QUOTA_BYTES -> bytesInRange(key, value, QUOTA_MIN_BYTES, quotaCeiling);
            case SITE_NAME -> {
                if (value.isEmpty() || value.length() > SITE_NAME_MAX) {
                    throw invalid(key, "Название от 1 до " + SITE_NAME_MAX + " символов");
                }
                if (CONTROL.matcher(value).find()) {
                    throw invalid(key, "Название не может содержать управляющие символы");
                }
                yield value;
            }
        };
    }

    public static String normalizeReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > REASON_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("reason", "Укажите причину от 1 до " + REASON_MAX + " символов")));
        }
        return trimmed;
    }

    private static String bytesInRange(AppSettingKey key, String value, long min, long max) {
        long bytes;
        try {
            bytes = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalid(key, "Укажите число байт");
        }
        if (bytes < min || bytes > max) {
            throw invalid(key, String.format(Locale.ROOT, "Допустимо от %d до %d байт", min, max));
        }
        return Long.toString(bytes);
    }

    private static ApiException invalid(AppSettingKey key, String message) {
        return ApiException.validation("Проверьте поля запроса", List.of(new FieldErrorDetail("value", message)));
    }
}
