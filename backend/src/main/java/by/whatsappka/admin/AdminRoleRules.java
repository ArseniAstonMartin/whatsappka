package by.whatsappka.admin;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Глобальные роли, которые администратор может выдавать и снимать. USER есть у всех и не управляется. */
public final class AdminRoleRules {

    public static final Set<String> MANAGED = Set.of("MODERATOR", "ADMIN");

    private AdminRoleRules() {
    }

    /** Список целевых ролей: только MODERATOR и ADMIN, повторы схлопываются, пустой список снимает все служебные роли. */
    public static Set<String> parseRoles(List<String> raw) {
        if (raw == null) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("roles", "Укажите список ролей")));
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : raw) {
            if (!MANAGED.contains(role)) {
                throw ApiException.validation("Проверьте поля запроса",
                        List.of(new FieldErrorDetail("roles", "Допустимы MODERATOR и ADMIN")));
            }
            roles.add(role);
        }
        return roles;
    }
}
