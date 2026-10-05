package by.whatsappka.content;

import by.whatsappka.platform.web.ApiException;
import java.util.Set;

/** Шесть типов реакций (FR-05); лайк — один из них, а не отдельный счётчик. */
final class ReactionRules {

    static final Set<String> TYPES = Set.of("LIKE", "HEART", "LAUGH", "WOW", "SAD", "SUPPORT");

    private ReactionRules() {
    }

    static String requireType(String raw) {
        String type = raw == null ? "" : raw.strip().toUpperCase(java.util.Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw ApiException.badRequest("invalid_reaction_type", "Неизвестный тип реакции");
        }
        return type;
    }
}
