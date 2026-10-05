package by.whatsappka.messaging;

/** SQL групповых чатов и истории изменений состава. */
final class GroupSql {

    private GroupSql() {
    }

    static final String LOCK_CONVERSATION = """
            SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS uuid)::text, 1))
            """;

    static final String INSERT_GROUP = """
            INSERT INTO conversations (id, type, title, owner_id, next_seq, next_event_seq, version, created_at, updated_at)
            VALUES (?, 'GROUP', ?, ?, 0, 0, 0, now(), now())
            """;

    /** Новый интервал членства: joined_seq берётся с текущего хвоста, так что история до вступления скрыта. */
    static final String INSERT_MEMBERSHIP = """
            INSERT INTO conversation_memberships (id, conversation_id, user_id, role, joined_seq, joined_at, last_read_seq)
            VALUES (?, ?, ?, ?, (SELECT next_seq FROM conversations WHERE id = ?), now(), 0)
            """;

    static final String CLOSE_MEMBERSHIP = """
            UPDATE conversation_memberships m
            SET left_at = now(), left_seq = (SELECT next_seq FROM conversations WHERE id = m.conversation_id)
            WHERE m.conversation_id = ? AND m.user_id = ? AND m.left_at IS NULL
            """;

    static final String ACTIVE_ROLE = """
            SELECT role FROM conversation_memberships
            WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL
            """;

    static final String OWNER_OF = """
            SELECT owner_id FROM conversations WHERE id = ? AND type = 'GROUP'
            """;

    static final String COUNT_ACTIVE = """
            SELECT count(*) FROM conversation_memberships WHERE conversation_id = ? AND left_at IS NULL
            """;

    static final String SET_ROLE = """
            UPDATE conversation_memberships SET role = ?
            WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL
            """;

    static final String SET_OWNER = """
            UPDATE conversations SET owner_id = ?, version = version + 1, updated_at = now() WHERE id = ?
            """;

    /** Каждое изменение состава получает следующий номер события чата. */
    static final String NEXT_EVENT_SEQ = """
            UPDATE conversations SET next_event_seq = next_event_seq + 1, version = version + 1, updated_at = now()
            WHERE id = ? RETURNING next_event_seq
            """;

    static final String INSERT_EVENT = """
            INSERT INTO conversation_events (id, conversation_id, event_seq, type, actor_id, subject_user_id, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            """;

    static final String SET_AVATAR = """
            UPDATE conversations SET avatar_media_id = ?, version = version + 1, updated_at = now() WHERE id = ?
            """;

    static final String SET_TITLE = """
            UPDATE conversations SET title = ?, version = version + 1, updated_at = now() WHERE id = ?
            """;

    static final String LIST_MEMBERS = """
            SELECT u.id, u.username, p.display_name, m.role
            FROM conversation_memberships m
            JOIN users u ON u.id = m.user_id
            JOIN user_profiles p ON p.user_id = u.id
            WHERE m.conversation_id = ? AND m.left_at IS NULL
            ORDER BY m.joined_at, u.id
            """;
}
