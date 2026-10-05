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
}
