package by.whatsappka.messaging;

/**
 * SQL чатов. Упорядочивание пары делает база (LEAST/GREATEST по байтам uuid), поэтому ограничение
 * user_low_id &lt; user_high_id и поиск совпадают между собой.
 */
final class ConversationSql {

    private ConversationSql() {
    }

    /** Сериализует создание диалога для одной пары: параллельные запросы ждут и видят уже созданный чат. */
    static final String LOCK_PAIR = """
            SELECT pg_advisory_xact_lock(hashtextextended(
                LEAST(CAST(? AS uuid), CAST(? AS uuid))::text || ':' || GREATEST(CAST(? AS uuid), CAST(? AS uuid))::text, 0))
            """;

    static final String FIND_DIRECT = """
            SELECT d.conversation_id
            FROM direct_conversations d
            WHERE d.user_low_id = LEAST(CAST(? AS uuid), CAST(? AS uuid))
              AND d.user_high_id = GREATEST(CAST(? AS uuid), CAST(? AS uuid))
            """;

    static final String INSERT_CONVERSATION = """
            INSERT INTO conversations (id, type, next_seq, next_event_seq, version, created_at, updated_at)
            VALUES (?, 'DIRECT', 0, 0, 0, now(), now())
            """;

    static final String INSERT_DIRECT = """
            INSERT INTO direct_conversations (conversation_id, user_low_id, user_high_id)
            VALUES (CAST(? AS uuid), LEAST(CAST(? AS uuid), CAST(? AS uuid)), GREATEST(CAST(? AS uuid), CAST(? AS uuid)))
            """;

    static final String INSERT_MEMBERSHIP = """
            INSERT INTO conversation_memberships (id, conversation_id, user_id, role, joined_seq, joined_at, last_read_seq)
            VALUES (?, ?, ?, 'MEMBER', 0, now(), 0)
            """;

    static final String IS_ACTIVE_MEMBER = """
            SELECT EXISTS (SELECT 1 FROM conversation_memberships
                           WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL)
            """;

    /** Превью — последнее видимое участнику неудалённое сообщение. Счётчик unread — чужие неудалённые после прочитанного. */
    private static final String LAST_MESSAGE_JOIN = """
            LEFT JOIN LATERAL (
                SELECT msg.seq, msg.body, msg.sender_id, msg.created_at
                FROM messages msg
                WHERE msg.conversation_id = c.id AND msg.seq > m.joined_seq AND msg.deleted_at IS NULL
                ORDER BY msg.seq DESC
                LIMIT 1
            ) lm ON true
            LEFT JOIN LATERAL (
                SELECT count(*) AS unread FROM messages msg
                WHERE msg.conversation_id = c.id
                  AND msg.seq > GREATEST(m.last_read_seq, m.joined_seq)
                  AND msg.sender_id <> m.user_id
                  AND msg.deleted_at IS NULL
            ) ur ON true
            """;

    /**
     * Личный диалог. Доступен, пока пользователь участник и собеседник активен. Блокировка не скрывает
     * историю: она возвращается флагом blocked, а отправку запрещает отправляющий путь.
     */
    private static final String DIRECT_COLUMNS = """
            SELECT c.id, c.updated_at, 'DIRECT' AS conv_type, u.id AS other_id, u.username, p.display_name,
                   NULL::varchar AS title, NULL::uuid AS avatar_media_id,
                   EXISTS (SELECT 1 FROM user_blocks b
                           WHERE (b.blocker_id = m.user_id AND b.blocked_id = u.id)
                              OR (b.blocker_id = u.id AND b.blocked_id = m.user_id)) AS blocked,
                   lm.seq AS last_seq, lm.body AS last_body, lm.sender_id AS last_sender_id,
                   lm.created_at AS last_created_at, ur.unread
            FROM conversation_memberships m
            JOIN conversations c ON c.id = m.conversation_id AND c.type = 'DIRECT'
            JOIN direct_conversations d ON d.conversation_id = c.id
            JOIN users u ON u.id = CASE WHEN d.user_low_id = m.user_id THEN d.user_high_id ELSE d.user_low_id END
            JOIN user_profiles p ON p.user_id = u.id
            """ + LAST_MESSAGE_JOIN + """
            WHERE m.user_id = ? AND m.left_at IS NULL AND u.status = 'ACTIVE'
            """;

    /** Групповой чат. Блокировка — понятие личного диалога, здесь её не бывает. */
    private static final String GROUP_COLUMNS = """
            SELECT c.id, c.updated_at, 'GROUP' AS conv_type, NULL::uuid AS other_id,
                   NULL::varchar AS username, NULL::varchar AS display_name,
                   c.title, c.avatar_media_id, false AS blocked,
                   lm.seq AS last_seq, lm.body AS last_body, lm.sender_id AS last_sender_id,
                   lm.created_at AS last_created_at, ur.unread
            FROM conversation_memberships m
            JOIN conversations c ON c.id = m.conversation_id AND c.type = 'GROUP'
            """ + LAST_MESSAGE_JOIN + """
            WHERE m.user_id = ? AND m.left_at IS NULL
            """;

    private static final String UNIONED = "SELECT * FROM ((" + DIRECT_COLUMNS + ") UNION ALL (" + GROUP_COLUMNS + ")) conv ";

    static final String LIST_FIRST = UNIONED + """
            ORDER BY updated_at DESC, id DESC
            LIMIT ?
            """;

    static final String LIST_AFTER = UNIONED + """
            WHERE (updated_at, id) < (?, ?)
            ORDER BY updated_at DESC, id DESC
            LIMIT ?
            """;

    static final String GET_FOR_MEMBER = UNIONED + """
            WHERE id = ?
            """;
}
