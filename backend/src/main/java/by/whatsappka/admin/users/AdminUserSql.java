package by.whatsappka.admin.users;

/** SQL управления пользователями. Блокировки строк фиксируют порядок изменений при параллельных запросах. */
final class AdminUserSql {

    private AdminUserSql() {
    }

    static final String LOCK_USER = "SELECT user_id FROM user_profiles WHERE user_id = ? FOR UPDATE";

    /** Все строки служебных ролей блокируются до подсчёта: два параллельных снятия роли не пройдут оба. */
    static final String LOCK_ADMIN_ROLES = "SELECT user_id FROM user_roles WHERE role_code = 'ADMIN' FOR UPDATE";

    static final String COUNT_ACTIVE_ADMINS = """
            SELECT count(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id
            WHERE ur.role_code = 'ADMIN' AND u.status = 'ACTIVE'
            """;

    static final String CURRENT_ROLES = "SELECT role_code FROM user_roles WHERE user_id = ?";

    static final String GRANT = """
            INSERT INTO user_roles (user_id, role_code, granted_at) VALUES (?, ?, now())
            ON CONFLICT (user_id, role_code) DO NOTHING
            """;

    static final String REVOKE = "DELETE FROM user_roles WHERE user_id = ? AND role_code = ?";

    static final String VERIFY = """
            UPDATE user_profiles SET verified_at = now(), verified_by = ?, updated_at = now()
            WHERE user_id = ? AND verified_at IS NULL
            """;

    static final String UNVERIFY = """
            UPDATE user_profiles SET verified_at = NULL, verified_by = NULL, updated_at = now()
            WHERE user_id = ? AND verified_at IS NOT NULL
            """;
}
