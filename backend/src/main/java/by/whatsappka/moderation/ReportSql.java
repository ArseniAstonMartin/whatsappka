package by.whatsappka.moderation;

/**
 * Доступ и снимок в одном запросе на каждый вид объекта: если зритель объект не видит, строки нет и ответ 404.
 * Снимок содержит только то, что нужно для решения. Для сообщения — только оно сам, не весь чат.
 */
final class ReportSql {

    private ReportSql() {
    }

    /** Параметры: targetId, viewer, viewer, viewer. Заблокированный или неактивный пользователь не виден. */
    static final String SNAPSHOT_USER = """
            SELECT jsonb_build_object('userId', u.id, 'username', u.username, 'displayName', p.display_name)::text
            FROM users u
            JOIN user_profiles p ON p.user_id = u.id
            WHERE u.id = ? AND u.status = 'ACTIVE'
              AND NOT EXISTS (SELECT 1 FROM user_blocks b
                              WHERE (b.blocker_id = ? AND b.blocked_id = u.id)
                                 OR (b.blocker_id = u.id AND b.blocked_id = ?))
            """;

    /** Параметры: postId, viewer, viewer. Пост виден, если он опубликован и группа доступна зрителю. */
    static final String SNAPSHOT_POST = """
            SELECT jsonb_build_object('postId', p.id, 'authorId', p.author_id, 'body', p.body, 'createdAt', p.created_at)::text
            FROM posts p
            WHERE p.id = ? AND p.status = 'PUBLISHED' AND p.deleted_at IS NULL
              AND (p.group_id IS NULL OR EXISTS (
                  SELECT 1 FROM groups g
                  WHERE g.id = p.group_id AND g.deleted_at IS NULL
                    AND ((g.visibility = 'PUBLIC' AND g.hidden_at IS NULL)
                         OR g.owner_id = ?
                         OR EXISTS (SELECT 1 FROM group_members m WHERE m.group_id = g.id AND m.user_id = ?))))
            """;

    /** Параметры: commentId, viewer, viewer. Удалённый комментарий и комментарий к недоступному посту не видны. */
    static final String SNAPSHOT_COMMENT = """
            SELECT jsonb_build_object('commentId', c.id, 'postId', c.post_id, 'authorId', c.author_id,
                                      'body', c.body, 'createdAt', c.created_at)::text
            FROM comments c
            JOIN posts p ON p.id = c.post_id
            WHERE c.id = ? AND c.deleted_at IS NULL AND p.status = 'PUBLISHED' AND p.deleted_at IS NULL
              AND (p.group_id IS NULL OR EXISTS (
                  SELECT 1 FROM groups g
                  WHERE g.id = p.group_id AND g.deleted_at IS NULL
                    AND ((g.visibility = 'PUBLIC' AND g.hidden_at IS NULL)
                         OR g.owner_id = ?
                         OR EXISTS (SELECT 1 FROM group_members m WHERE m.group_id = g.id AND m.user_id = ?))))
            """;

    /** Параметры: viewer, messageId. Сообщение видно действующему участнику, для которого оно уже отправлено. */
    static final String SNAPSHOT_MESSAGE = """
            SELECT jsonb_build_object('messageId', msg.id, 'conversationId', msg.conversation_id,
                                      'senderId', msg.sender_id, 'body', msg.body, 'createdAt', msg.created_at,
                                      'attachmentCount', (SELECT count(*) FROM message_media mm WHERE mm.message_id = msg.id))::text
            FROM messages msg
            JOIN conversation_memberships m
              ON m.conversation_id = msg.conversation_id AND m.user_id = ? AND m.left_at IS NULL
             AND msg.seq > m.joined_seq
            WHERE msg.id = ? AND msg.deleted_at IS NULL
            """;

    /**
     * Параметры: groupId, viewer, viewer. Приватная группа без участия зрителя не видна и не жалуется.
     * Описание попадает в снимок только у публичной группы.
     */
    static final String SNAPSHOT_GROUP = """
            SELECT (jsonb_build_object('groupId', g.id, 'slug', g.slug, 'name', g.name, 'visibility', g.visibility)
                    || CASE WHEN g.visibility = 'PUBLIC' THEN jsonb_build_object('description', g.description)
                            ELSE '{}'::jsonb END)::text
            FROM groups g
            WHERE g.id = ? AND g.deleted_at IS NULL
              AND ((g.visibility = 'PUBLIC' AND g.hidden_at IS NULL)
                   OR g.owner_id = ?
                   OR EXISTS (SELECT 1 FROM group_members m WHERE m.group_id = g.id AND m.user_id = ?))
            """;

    static final String INSERT_REPORT = """
            INSERT INTO reports (id, reporter_id, target_kind, target_user_id, target_post_id, target_comment_id,
                                 target_message_id, target_group_id, reason, description, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', now())
            """;

    static final String INSERT_EVIDENCE = """
            INSERT INTO report_evidence (id, report_id, kind, snapshot, captured_at)
            VALUES (?, ?, 'SNAPSHOT', CAST(? AS jsonb), now())
            """;

    static final String REPORT_WITH_EVIDENCE = """
            SELECT r.id, r.target_kind, r.reason, r.status, r.created_at, e.snapshot::text AS snapshot
            FROM reports r
            LEFT JOIN report_evidence e ON e.report_id = r.id
            WHERE r.id = ?
            """;
}
