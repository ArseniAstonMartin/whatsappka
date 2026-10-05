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
            INSERT INTO notifications (id, recipient_id, event_key, type, actor_id, target_kind, target_id, message_seq, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
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
                                                             AND i.status = 'PENDING' AND i.expires_at > now())
                       WHEN 'COMMENT' THEN EXISTS (SELECT 1 FROM posts p
                                                   WHERE p.id = n.target_id AND p.deleted_at IS NULL)
                       WHEN 'REPLY' THEN EXISTS (SELECT 1 FROM comments c JOIN posts p ON p.id = c.post_id
                                                 WHERE c.id = n.target_id AND c.deleted_at IS NULL AND p.deleted_at IS NULL)
                       WHEN 'REACTION' THEN CASE WHEN n.target_kind = 'POST'
                           THEN EXISTS (SELECT 1 FROM posts p WHERE p.id = n.target_id AND p.deleted_at IS NULL)
                           ELSE EXISTS (SELECT 1 FROM comments c JOIN posts p ON p.id = c.post_id
                                        WHERE c.id = n.target_id AND c.deleted_at IS NULL AND p.deleted_at IS NULL)
                       END
                       WHEN 'JOIN_REQUEST' THEN EXISTS (SELECT 1 FROM group_members g
                                                        WHERE g.group_id = n.target_id AND g.user_id = n.recipient_id
                                                          AND g.role = 'ADMIN')
                       WHEN 'COMMUNITY_INVITATION' THEN EXISTS (SELECT 1 FROM group_invitations i
                                                                WHERE i.group_id = n.target_id AND i.invitee_id = n.recipient_id
                                                                  AND i.status = 'PENDING' AND i.expires_at > now())
                       WHEN 'JOIN_RESULT' THEN EXISTS (SELECT 1 FROM groups g
                                                       WHERE g.id = n.target_id AND g.deleted_at IS NULL)
                       ELSE false
                   END AS target_visible,
                   CASE WHEN n.target_kind = 'POST' THEN n.target_id
                        WHEN n.target_kind = 'COMMENT' THEN (SELECT c.post_id FROM comments c WHERE c.id = n.target_id)
                   END AS link_post_id,
                   CASE WHEN n.target_kind = 'COMMUNITY' THEN (SELECT g.slug FROM groups g WHERE g.id = n.target_id)
                   END AS link_group_slug
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

    static final String CHAT_INVITER = """
            SELECT inviter_id FROM conversation_invitations WHERE id = ?
            """;

    static final String COMMUNITY_INVITER = """
            SELECT inviter_id FROM group_invitations WHERE id = ?
            """;

    static final String COMMUNITY_ADMINS = """
            SELECT owner_id FROM groups WHERE id = ? AND deleted_at IS NULL
            UNION
            SELECT user_id FROM group_members WHERE group_id = ? AND role = 'ADMIN'
            """;

    static final String MESSAGE_REF = """
            SELECT sender_id, seq, conversation_id FROM messages WHERE id = ? AND deleted_at IS NULL
            """;

    /** Участники, которые уже были в чате на момент сообщения (joined_seq раньше seq), кроме отправителя. */
    static final String MESSAGE_RECIPIENTS = """
            SELECT m.user_id FROM conversation_memberships m
            JOIN users u ON u.id = m.user_id AND u.status = 'ACTIVE'
            WHERE m.conversation_id = ? AND m.left_at IS NULL AND m.user_id <> ? AND m.joined_seq < ?
            """;

    /** Автор поста для уведомления о комментарии; удалённый пост уведомления не порождает. */
    static final String POST_AUTHOR = """
            SELECT author_id FROM posts WHERE id = ? AND deleted_at IS NULL
            """;

    /** Прочтение чата до seq снимает уведомления о сообщениях этого чата до того же seq. */
    static final String MARK_CHAT_READ = """
            UPDATE notifications SET read_at = now()
            WHERE recipient_id = ? AND type = 'MESSAGE' AND target_id = ? AND message_seq <= ? AND read_at IS NULL
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
