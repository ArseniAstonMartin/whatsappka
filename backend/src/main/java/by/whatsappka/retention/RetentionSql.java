package by.whatsappka.retention;

/**
 * SQL очистки. Сроки — из PRD: идемпотентность 24 часа, завершённые задания и события 7 дней, уведомления 90 дней,
 * аудит и снимки жалоб 180 дней. Живая ссылка на медиа — любая из перечисленных таблиц или колонок.
 */
final class RetentionSql {

    private RetentionSql() {
    }

    static final String IDEMPOTENCY = "DELETE FROM idempotency_records WHERE expires_at < ?";

    static final String FINISHED_JOBS = "DELETE FROM background_jobs WHERE status IN ('DONE', 'FAILED') AND updated_at < ?";

    static final String NOTIFICATIONS = "DELETE FROM notifications WHERE created_at < ?";

    /** Курс очистки событий: граница поднимается до последнего удалённого номера, клиент с меньшим курсором перечитает чат. */
    static final String EVENT_FLOOR = """
            UPDATE conversations c SET events_floor = GREATEST(c.events_floor, x.max_seq)
            FROM (SELECT conversation_id, MAX(event_seq) AS max_seq FROM conversation_events
                  WHERE occurred_at < ? GROUP BY conversation_id) x
            WHERE c.id = x.conversation_id
            """;

    static final String EVENTS = "DELETE FROM conversation_events WHERE occurred_at < ?";

    /** История refresh-токенов живёт до конца семейства сессии (абсолютного срока), затем уходит вместе с сессией. */
    static final String REFRESH_TOKENS = """
            DELETE FROM refresh_tokens rt USING auth_sessions s
            WHERE rt.session_id = s.id AND s.absolute_expires_at < ?
            """;

    static final String SESSIONS = """
            DELETE FROM auth_sessions s
            WHERE s.absolute_expires_at < ? AND NOT EXISTS (SELECT 1 FROM refresh_tokens r WHERE r.session_id = s.id)
            """;

    static final String ACCOUNT_TOKENS = "DELETE FROM account_tokens WHERE expires_at < ?";

    static final String REPORT_EVIDENCE = """
            DELETE FROM report_evidence e USING reports r
            WHERE e.report_id = r.id AND r.status IN ('RESOLVED', 'REJECTED') AND r.decided_at < ?
            """;

    static final String RESERVATIONS = "DELETE FROM media_reservations WHERE expires_at < ?";

    static final String AUDIT = "SELECT retention_purge_audit(?)";

    /** Медиа без живых ссылок. Проверка — отдельным фрагментом, чтобы использовать и при пометке, и при удалении. */
    static final String NO_LIVE_REFERENCE = """
            NOT EXISTS (SELECT 1 FROM media_links l WHERE l.media_id = a.id)
            AND NOT EXISTS (SELECT 1 FROM post_media p WHERE p.media_id = a.id)
            AND NOT EXISTS (SELECT 1 FROM message_media m WHERE m.media_id = a.id)
            AND NOT EXISTS (SELECT 1 FROM user_profiles up WHERE up.avatar_media_id = a.id OR up.cover_media_id = a.id)
            AND NOT EXISTS (SELECT 1 FROM conversations c WHERE c.avatar_media_id = a.id)
            AND NOT EXISTS (SELECT 1 FROM groups g WHERE g.avatar_media_id = a.id OR g.cover_media_id = a.id)
            """;

    /** Незакреплённые загрузки старше суток помечаются удалёнными; файлы уйдут при очистке. */
    static final String MARK_ORPHAN_UPLOADS = """
            UPDATE media_assets a SET deleted_at = ?, updated_at = ?
            WHERE a.deleted_at IS NULL AND a.created_at < ? AND
            """ + NO_LIVE_REFERENCE;

    static final String DELETED_MEDIA = """
            SELECT a.id, a.original_object_key FROM media_assets a
            WHERE a.deleted_at IS NOT NULL AND
            """ + NO_LIVE_REFERENCE + " ORDER BY a.deleted_at LIMIT ?";

    static final String VARIANT_KEYS = "SELECT object_key FROM media_variants WHERE media_id = ?";

    static final String DELETE_VARIANT_ROWS = "DELETE FROM media_variants WHERE media_id = ?";

    /** Проверка внутри той же транзакции, что и удаление: между ними никто не успеет закрепить файл. */
    static final String STILL_UNREFERENCED = """
            SELECT count(*) FROM media_assets a WHERE a.id = ? AND
            """ + NO_LIVE_REFERENCE;

    static final String DELETE_MEDIA_ROW = "DELETE FROM media_assets WHERE id = ?";
}
