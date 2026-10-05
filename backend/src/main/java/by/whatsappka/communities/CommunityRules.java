package by.whatsappka.communities;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Правила полей и ролей сообщества (FR-07, PRD §3). */
final class CommunityRules {

    static final int SLUG_MIN = 3;
    static final int SLUG_MAX = 60;
    static final int NAME_MAX = 100;
    static final int DESCRIPTION_MAX = 2000;

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]*[a-z0-9]$");

    enum Role { OWNER, ADMIN, MEMBER }

    private CommunityRules() {
    }

    static String normalizeSlug(String raw) {
        String slug = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (slug.length() < SLUG_MIN || slug.length() > SLUG_MAX || !SLUG.matcher(slug).matches()) {
            throw ApiException.validation("Проверьте поля запроса", List.of(new FieldErrorDetail(
                    "slug", "Адрес 3-60 символов: латиница, цифры и дефис, не на краях")));
        }
        return slug;
    }

    static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > NAME_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("name", "Название от 1 до 100 символов")));
        }
        return name;
    }

    static String normalizeDescription(String raw) {
        String description = raw == null ? "" : raw.trim();
        if (description.length() > DESCRIPTION_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("description", "Описание до 2000 символов")));
        }
        return description;
    }

    static String normalizeVisibility(String raw) {
        if ("PUBLIC".equals(raw) || "PRIVATE".equals(raw)) {
            return raw;
        }
        throw ApiException.validation("Проверьте поля запроса",
                List.of(new FieldErrorDetail("visibility", "Допустимы PUBLIC или PRIVATE")));
    }
}
