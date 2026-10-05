package by.whatsappka.sanctions;

/** SQL санкций. Статус аккаунта меняется только вместе с записью санкции и только при наличии активной санкции. */
final class SanctionSql {

    private SanctionSql() {
    }

    static final String LOCK_USER = "SELECT status FROM users WHERE id = ? FOR UPDATE";

    static final String INSERT = """
            INSERT INTO user_sanctions (id, user_id, kind, reason, issued_by, issued_at, expires_at)
            VALUES (?, ?, ?, ?, ?, now(), CASE WHEN CAST(? AS integer) IS NULL THEN NULL ELSE now() + make_interval(hours => CAST(? AS integer)) END)
            RETURNING expires_at
            """;

    static final String SUSPEND = "UPDATE users SET status = 'SUSPENDED', updated_at = now() WHERE id = ? AND status = 'ACTIVE'";

    static final String OWNER_OF_SANCTION = "SELECT user_id FROM user_sanctions WHERE id = ?";

    /** Снятие действует только на действующую санкцию: уже снятая или истёкшая — конфликт. */
    static final String LIFT = """
            UPDATE user_sanctions SET lifted_at = now(), lifted_by = ?, lift_reason = ?
            WHERE id = ? AND lifted_at IS NULL AND (expires_at IS NULL OR expires_at > now())
            """;

    static final String HAS_ACTIVE_SANCTION = """
            SELECT EXISTS (SELECT 1 FROM user_sanctions s
                           WHERE s.user_id = ? AND s.lifted_at IS NULL AND (s.expires_at IS NULL OR s.expires_at > now()))
            """;

    static final String RESTORE_ACCOUNT = "UPDATE users SET status = 'ACTIVE', updated_at = now() WHERE id = ? AND status = 'SUSPENDED'";

    /** Истечение срока: отмечает санкции и возвращает пользователей, у которых больше нет действующих санкций. */
    static final String EXPIRE = """
            UPDATE user_sanctions SET lifted_at = now(), lift_reason = 'срок истёк'
            WHERE lifted_at IS NULL AND expires_at IS NOT NULL AND expires_at <= now()
            RETURNING id, user_id
            """;

    /** Вернуть ACTIVE только тому, у кого не осталось действующих санкций. */
    static final String RESTORE_ACCOUNT_IF_FREE = """
            UPDATE users SET status = 'ACTIVE', updated_at = now()
            WHERE id = ? AND status = 'SUSPENDED'
              AND NOT EXISTS (SELECT 1 FROM user_sanctions s
                              WHERE s.user_id = users.id AND s.lifted_at IS NULL
                                AND (s.expires_at IS NULL OR s.expires_at > now()))
            """;

    static final String INSERT_AUDIT = """
            INSERT INTO audit_logs (id, actor_id, action, target_type, target_id, reason, occurred_at, trace_id)
            VALUES (?, NULL, ?, 'user', ?, ?, now(), 'sanction-sweeper')
            """;
}
