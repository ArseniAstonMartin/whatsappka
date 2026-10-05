package by.whatsappka.moderation;

/**
 * SQL решений. Каждый переход состояния — сравнение-и-замена: повторный вызов не находит нужной строки
 * и ничего не меняет, поэтому повтор решения не повторяет действие.
 */
final class ModerationSql {

    private ModerationSql() {
    }

    /** Блокировка жалобы на время решения: два модератора не решат одну жалобу параллельно. */
    static final String LOCK_REPORT = """
            SELECT r.reporter_id, r.status, r.assignee_id, r.target_kind, r.target_user_id, r.target_post_id,
                   r.target_comment_id, r.target_message_id, r.target_group_id
            FROM reports r WHERE r.id = ? FOR UPDATE
            """;

    static final String TAKE = """
            UPDATE reports SET status = 'IN_REVIEW', assignee_id = ? WHERE id = ? AND status = 'OPEN'
            """;

    static final String DECIDE = """
            UPDATE reports SET status = ?, decided_at = now() WHERE id = ? AND status = 'IN_REVIEW' AND assignee_id = ?
            """;

    static final String HIDE_POST = """
            UPDATE posts SET status = 'HIDDEN', updated_at = now() WHERE id = ? AND status = 'PUBLISHED'
            """;

    static final String HIDE_COMMENT = """
            UPDATE comments SET hidden_at = now() WHERE id = ? AND hidden_at IS NULL
            """;

    static final String HIDE_GROUP = """
            UPDATE groups SET hidden_at = now(), version = version + 1, updated_at = now()
            WHERE id = ? AND hidden_at IS NULL AND deleted_at IS NULL
            """;

    static final String RESTORE_POST = """
            UPDATE posts SET status = 'PUBLISHED', updated_at = now() WHERE id = ? AND status = 'HIDDEN'
            """;

    static final String RESTORE_COMMENT = """
            UPDATE comments SET hidden_at = NULL WHERE id = ? AND hidden_at IS NOT NULL
            """;

    static final String RESTORE_GROUP = """
            UPDATE groups SET hidden_at = NULL, version = version + 1, updated_at = now()
            WHERE id = ? AND hidden_at IS NOT NULL
            """;

    static final String AUTHOR_OF_POST = "SELECT author_id FROM posts WHERE id = ?";
    static final String AUTHOR_OF_COMMENT = "SELECT author_id FROM comments WHERE id = ?";
    static final String OWNER_OF_GROUP = "SELECT owner_id FROM groups WHERE id = ?";

    static final String INSERT_ACTION = """
            INSERT INTO moderation_actions (id, report_id, actor_id, action, from_status, to_status, reason, created_at, trace_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, now(), ?)
            """;

    /** Последнее действие, меняющее скрытие: отсюда видно, можно ли восстанавливать. */
    static final String LAST_HIDE_ACTION = """
            SELECT action FROM moderation_actions
            WHERE report_id = ? AND action IN ('RESOLVE_HIDE', 'RESTORE')
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """;

    static final String QUEUE_FIRST = """
            SELECT id, target_kind, reason, status, created_at, assignee_id, decided_at
            FROM reports WHERE status = ?
            ORDER BY created_at, id
            LIMIT ?
            """;

    static final String QUEUE_AFTER = """
            SELECT id, target_kind, reason, status, created_at, assignee_id, decided_at
            FROM reports WHERE status = ? AND (created_at, id) > (CAST(? AS timestamptz), CAST(? AS uuid))
            ORDER BY created_at, id
            LIMIT ?
            """;
}
