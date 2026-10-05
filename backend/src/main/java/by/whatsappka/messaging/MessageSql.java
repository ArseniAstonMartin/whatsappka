package by.whatsappka.messaging;

/** SQL отправки сообщений. */
final class MessageSql {

    private MessageSql() {
    }

    static final String FIND_BY_CLIENT_ID = """
            SELECT id, seq, fingerprint, created_at FROM messages
            WHERE conversation_id = ? AND sender_id = ? AND client_message_id = ?
            """;

    static final String NEXT_SEQ = """
            UPDATE conversations SET next_seq = next_seq + 1, version = version + 1, updated_at = now()
            WHERE id = ? RETURNING next_seq
            """;

    static final String INSERT_MESSAGE = """
            INSERT INTO messages (id, conversation_id, sender_id, seq, client_message_id, body, fingerprint, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
            """;

    static final String INSERT_ATTACHMENT = """
            INSERT INTO message_media (message_id, media_id, position) VALUES (?, ?, ?)
            """;

    static final String MEDIA_FOR_ATTACHMENT = """
            SELECT owner_id, status, purpose, deleted_at,
                   (SELECT count(*) FROM message_media mm WHERE mm.media_id = m.id) AS attached
            FROM media_assets m WHERE m.id = ?
            """;

    static final String ACTIVE_MEMBERS = """
            SELECT user_id FROM conversation_memberships WHERE conversation_id = ? AND left_at IS NULL
            """;

    static final String DIRECT_COUNTERPART = """
            SELECT CASE WHEN d.user_low_id = ? THEN d.user_high_id ELSE d.user_low_id END
            FROM direct_conversations d WHERE d.conversation_id = ?
            """;

    static final String INSERT_LINK = """
            INSERT INTO media_links (media_id, link_type, link_id, created_at)
            VALUES (?, 'CHAT_ATTACHMENT', ?, now())
            """;

    /** Вложение видно только действующему участнику, для чьего интервала сообщение уже отправлено. */
    static final String CAN_VIEW_ATTACHMENT = """
            SELECT EXISTS (SELECT 1 FROM messages msg
                           JOIN conversation_memberships m
                             ON m.conversation_id = msg.conversation_id AND m.user_id = ? AND m.left_at IS NULL
                           WHERE msg.id = ? AND msg.deleted_at IS NULL AND msg.seq > m.joined_seq)
            """;

    static final String ACTIVE_JOINED_SEQ = """
            SELECT joined_seq, id FROM conversation_memberships
            WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL
            """;

    /**
     * История по seq от новых к старым. Берёт только сообщения после начала активного интервала участника.
     * Блокировку здесь не проверяем: история личного диалога сохраняется.
     */
    static final String HISTORY_FIRST = """
            SELECT msg.id, msg.seq, msg.sender_id, msg.client_message_id, msg.body, msg.created_at, msg.updated_at, msg.version, msg.deleted_at
            FROM messages msg
            WHERE msg.conversation_id = ? AND msg.seq > ?
            ORDER BY msg.seq DESC
            LIMIT ?
            """;

    static final String HISTORY_BEFORE = """
            SELECT msg.id, msg.seq, msg.sender_id, msg.client_message_id, msg.body, msg.created_at, msg.updated_at, msg.version, msg.deleted_at
            FROM messages msg
            WHERE msg.conversation_id = ? AND msg.seq > ? AND msg.seq < ?
            ORDER BY msg.seq DESC
            LIMIT ?
            """;

    /** Вложения страницы; список id подставляет вызывающий код. Скрыты вложения удалённых файлов и сообщений. */
    static final String ATTACHMENTS_PREFIX = """
            SELECT mm.message_id, mm.media_id, mm.position, a.purpose
            FROM message_media mm
            JOIN media_assets a ON a.id = mm.media_id
            JOIN messages msg ON msg.id = mm.message_id
            WHERE a.deleted_at IS NULL AND msg.deleted_at IS NULL AND mm.message_id IN (
            """;

    static final String ATTACHMENTS_SUFFIX = """
            ) ORDER BY mm.message_id, mm.position
            """;

    static final String SNAPSHOTS_BY_ID = """
            SELECT msg.id, msg.seq, msg.sender_id, msg.client_message_id, msg.body, msg.created_at, msg.updated_at, msg.version, msg.deleted_at
            FROM messages msg
            WHERE msg.conversation_id = ? AND msg.seq > ? AND msg.id IN (%s)
            ORDER BY msg.seq
            """;

    static final String NEXT_EVENT_SEQ = """
            UPDATE conversations SET next_event_seq = next_event_seq + 1, version = version + 1, updated_at = now()
            WHERE id = ? RETURNING next_event_seq
            """;

    static final String INSERT_MESSAGE_EVENT = """
            INSERT INTO conversation_events (id, conversation_id, event_seq, type, actor_id, message_id, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            """;

    /** Сообщение блокируется для правки и удаления; проверка идёт внутри блокировки чата. */
    static final String LOAD_FOR_CHANGE = """
            SELECT sender_id, created_at, deleted_at FROM messages
            WHERE id = ? AND conversation_id = ?
            FOR UPDATE
            """;

    static final String ATTACHMENT_IDS = """
            SELECT media_id FROM message_media WHERE message_id = ? ORDER BY position
            """;

    static final String EDIT_TEXT = """
            UPDATE messages SET body = ?, version = version + 1, updated_at = now()
            WHERE id = ?
            RETURNING version, updated_at
            """;

    static final String DELETE_MESSAGE = """
            UPDATE messages SET body = NULL, deleted_at = now(), version = version + 1, updated_at = now()
            WHERE id = ?
            RETURNING version, updated_at
            """;

    /** Модератор чата: владелец или действующий участник с ролью ADMIN. */
    static final String IS_CHAT_ADMIN = """
            SELECT EXISTS (SELECT 1 FROM conversations c WHERE c.id = ? AND c.owner_id = ?)
                OR EXISTS (SELECT 1 FROM conversation_memberships m
                           WHERE m.conversation_id = ? AND m.user_id = ? AND m.left_at IS NULL AND m.role = 'ADMIN')
            """;

    static final String INSERT_AUDIT = """
            INSERT INTO message_audit (id, message_id, conversation_id, actor_id, action, reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            """;

    static final String EVENT_CURSOR_STATE = """
            SELECT events_floor, next_event_seq FROM conversations WHERE id = ?
            """;

    /**
     * События после курсора. Событие сообщения видно участнику, только если сообщение отправлено после начала его интервала.
     */
    static final String EVENTS_AFTER = """
            SELECT e.event_seq, e.type, e.occurred_at, e.actor_id, e.message_id, msg.seq AS message_seq
            FROM conversation_events e
            LEFT JOIN messages msg ON msg.id = e.message_id
            WHERE e.conversation_id = ? AND e.event_seq > ?
              AND (e.message_id IS NULL OR msg.seq > ?)
            ORDER BY e.event_seq
            LIMIT ?
            """;

    /**
     * Прочтение. Прогресс только растёт (GREATEST) и не выходит за последний существующий seq чата (LEAST).
     * Возвращает новое значение; 0 строк — участника нет.
     */
    static final String READ_UPDATE = """
            UPDATE conversation_memberships m
            SET last_read_seq = GREATEST(m.last_read_seq,
                    LEAST(?, COALESCE((SELECT max(msg.seq) FROM messages msg WHERE msg.conversation_id = m.conversation_id), 0)))
            WHERE m.conversation_id = ? AND m.user_id = ? AND m.left_at IS NULL
            RETURNING m.last_read_seq
            """;

    /**
     * Непрочитанные для участника: сообщения после начала интервала и после прочитанного, чужие и не удалённые.
     */
    static final String UNREAD_FOR_MEMBER = """
            SELECT m.last_read_seq,
                   (SELECT count(*) FROM messages msg
                    WHERE msg.conversation_id = m.conversation_id
                      AND msg.seq > GREATEST(m.last_read_seq, m.joined_seq)
                      AND msg.sender_id <> m.user_id
                      AND msg.deleted_at IS NULL) AS unread
            FROM conversation_memberships m
            WHERE m.conversation_id = ? AND m.user_id = ? AND m.left_at IS NULL
            """;

    /**
     * Сколько прочитало сообщение среди тех, кто имел к нему доступ в момент отправки (кроме автора).
     * Участник имел доступ, если сообщение пришло после его входа и не после его выхода.
     */
    static final String READ_STATUS = """
            SELECT count(*) FILTER (WHERE m.last_read_seq >= msg.seq) AS read_by,
                   count(*) AS eligible
            FROM messages msg
            JOIN conversation_memberships m
              ON m.conversation_id = msg.conversation_id
             AND m.user_id <> msg.sender_id
             AND m.joined_seq < msg.seq
             AND (m.left_seq IS NULL OR m.left_seq >= msg.seq)
            WHERE msg.id = ? AND msg.conversation_id = ?
            """;
}
