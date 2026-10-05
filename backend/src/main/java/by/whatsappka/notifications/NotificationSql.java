package by.whatsappka.notifications;

/** SQL уведомлений. Подробности объекта видны по текущему доступу получателя, а не по тому, что было при создании. */
final class NotificationSql {

    private NotificationSql() {
    }

    static final String PREFERENCES_OF = """
            SELECT type, enabled FROM notification_preferences WHERE user_id = ?
            """;

    static final String PREFERENCE_ENABLED = """
            SELECT enabled FROM notification_preferences WHERE user_id = ? AND type = ?
            """;

    static final String UPSERT_PREFERENCE = """
            INSERT INTO notification_preferences (user_id, type, enabled, updated_at)
            VALUES (?, ?, ?, now())
            ON CONFLICT (user_id, type) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = now()
            """;

    /** Повтор того же события тому же получателю тем же типом не создаёт второе уведомление. */
    static final String INSERT_NOTIFICATION = """
            INSERT INTO notifications (id, recipient_id, event_key, type, actor_id, target_kind, target_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (event_key, recipient_id, type) DO NOTHING
            """;

    private static final String LIST_SELECT = """
            SELECT n.id, n.type, n.created_at, n.read_at, n.target_kind, n.target_id,
                   a.id AS actor_id, a.username AS actor_username, p.display_name AS actor_display_name,
                   EXISTS (SELECT 1 FROM user_blocks b
                           WHERE (b.blocker_id = n.recipient_id AND b.blocked_id = n.actor_id)
                              OR (b.blocker_id = n.actor_id AND b.blocked_id = n.recipient_id)) AS actor_blocked,
                   CASE n.type
                       WHEN 'FOLLOW' THEN true
                       WHEN 'MESSAGE' THEN EXISTS (SELECT 1 FROM conversation_memberships m
                                                   WHERE m.conversation_id = n.target_id AND m.user_id = n.recipient_id
                                                     AND m.left_at IS NULL)
                       WHEN 'CHAT_INVITATION' THEN EXISTS (SELECT 1 FROM conversation_invitations i
                                                           WHERE i.conversation_id = n.target_id AND i.invitee_id = n.recipient_id
                                                             AND i.status = 'PENDING')
                       WHEN 'JOIN_REQUEST' THEN EXISTS (SELECT 1 FROM group_members g
                                                        WHERE g.group_id = n.target_id AND g.user_id = n.recipient_id
                                                          AND g.role = 'ADMIN')
                       WHEN 'COMMUNITY_INVITATION' THEN EXISTS (SELECT 1 FROM group_members g
                                                                WHERE g.group_id = n.target_id AND g.user_id = n.recipient_id)
                       ELSE false
                   END AS target_visible
            FROM notifications n
            LEFT JOIN users a ON a.id = n.actor_id
            LEFT JOIN user_profiles p ON p.user_id = a.id
            WHERE n.recipient_id = ?
            """;

    static final String LIST_FIRST = LIST_SELECT + """
            ORDER BY n.created_at DESC, n.id DESC
            LIMIT ?
            """;

    static final String LIST_AFTER = LIST_SELECT + """
              AND (n.created_at, n.id) < (?, ?)
            ORDER BY n.created_at DESC, n.id DESC
            LIMIT ?
            """;

    static final String UNREAD_COUNT = """
            SELECT count(*) FROM notifications WHERE recipient_id = ? AND read_at IS NULL
            """;

    static final String MARK_READ = """
            UPDATE notifications SET read_at = COALESCE(read_at, now()) WHERE id = ? AND recipient_id = ?
            """;

    static final String MARK_ALL_READ = """
            UPDATE notifications SET read_at = now() WHERE recipient_id = ? AND read_at IS NULL
            """;
}
