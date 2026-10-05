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
            SELECT joined_seq FROM conversation_memberships
            WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL
            """;

    /**
     * История по seq от новых к старым. Берёт только сообщения после начала активного интервала участника.
     * Блокировку здесь не проверяем: история личного диалога сохраняется.
     */
    static final String HISTORY_FIRST = """
            SELECT msg.id, msg.seq, msg.sender_id, msg.body, msg.created_at, msg.deleted_at
            FROM messages msg
            WHERE msg.conversation_id = ? AND msg.seq > ?
            ORDER BY msg.seq DESC
            LIMIT ?
            """;

    static final String HISTORY_BEFORE = """
            SELECT msg.id, msg.seq, msg.sender_id, msg.body, msg.created_at, msg.deleted_at
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
}
