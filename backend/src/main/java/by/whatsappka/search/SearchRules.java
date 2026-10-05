package by.whatsappka.search;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Правила поиска. normalize должна давать тот же результат, что SQL-функция whatsappka_search_norm (V27):
 * это проверяется тестом на совпадение значений.
 */
public final class SearchRules {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;
    public static final int PAGE_MAX = 20;
    public static final int PAGE_DEFAULT = 20;

    private SearchRules() {
    }

    /** NFKC, нижний регистр, і→и, ў→у, ё→е. */
    public static String normalize(String raw) {
        String text = Normalizer.normalize(raw == null ? "" : raw.strip(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(switch (c) {
                case 'і' -> 'и';
                case 'ў' -> 'у';
                case 'ё' -> 'е';
                default -> c;
            });
        }
        return out.toString();
    }

    /** Запрос после нормализации должен быть от двух до ста символов. */
    public static String requireQuery(String raw) {
        String normalized = normalize(raw);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("q", "Запрос от 2 до 100 символов")));
        }
        return normalized;
    }

    /**
     * Шаблон LIKE без подстановок от пользователя: символы % и _ экранируются, экранирующий — восклицательный знак.
     * Обратный слэш не используется, чтобы не зависеть от правил строковых литералов.
     */
    public static String pattern(String normalized, boolean prefix) {
        String escaped = normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return prefix ? escaped + "%" : "%" + escaped + "%";
    }

    public static int pageSize(Integer requested) {
        if (requested == null) {
            return PAGE_DEFAULT;
        }
        if (requested < 1) {
            throw ApiException.badRequest("invalid_page_size", "Размер страницы должен быть не меньше 1");
        }
        return Math.min(requested, PAGE_MAX);
    }
}
