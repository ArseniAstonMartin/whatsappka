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

    /** Исключать может владелец любого, кроме себя; администратор — только рядовых участников. */
    static boolean canRemove(Role actor, Role target) {
        if (actor == Role.OWNER) {
            return target != Role.OWNER;
        }
        return actor == Role.ADMIN && target == Role.MEMBER;
    }

    /** Назначать и снимать администраторов может только владелец. Владельца это не касается. */
    static boolean canSetRole(Role actor, Role target) {
        return actor == Role.OWNER && target != Role.OWNER;
    }

    static boolean canTransferOwnership(Role actor) {
        return actor == Role.OWNER;
    }

    /** Владелец не выходит, пока не передал владение или не удалил сообщество. */
    static boolean canLeave(Role actor) {
        return actor != Role.OWNER;
    }

    /** Приглашать в сообщество могут владелец и администраторы. */
    static boolean canInvite(Role actor) {
        return actor == Role.OWNER || actor == Role.ADMIN;
    }

    static Role parseAssignableRole(String raw) {
        if ("ADMIN".equals(raw)) {
            return Role.ADMIN;
        }
        if ("MEMBER".equals(raw)) {
            return Role.MEMBER;
        }
        throw ApiException.validation("Проверьте поля запроса",
                List.of(new FieldErrorDetail("role", "Допустимы ADMIN или MEMBER")));
    }
}
