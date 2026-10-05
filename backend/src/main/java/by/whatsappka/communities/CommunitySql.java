package by.whatsappka.communities;

/** SQL сообществ. Константы вынесены, чтобы их можно было проверить отдельно от Spring. */
final class CommunitySql {

    private CommunitySql() {
    }

    static final String LOCK_GROUP = """
            SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS uuid)::text, 2))
            """;

    static final String INSERT_GROUP = """
            INSERT INTO groups (id, slug, name, description, visibility, owner_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, now(), now())
            """;

    /** Владелец тоже получает строку членства, но его полномочия определяет groups.owner_id, не роль здесь. */
    static final String INSERT_MEMBER = """
            INSERT INTO group_members (group_id, user_id, role, joined_at)
            VALUES (?, ?, 'ADMIN', now())
            """;

    /** Достаточно для авторизации, каталога и ограниченного вида приватной группы снаружи. Не кэшируется. */
    static final String ROW_BY_ID = """
            SELECT id, slug, name, owner_id, visibility, hidden_at, deleted_at
            FROM groups WHERE id = ?
            """;

    static final String ROW_BY_SLUG = """
            SELECT id, slug, name, owner_id, visibility, hidden_at, deleted_at
            FROM groups WHERE lower(slug) = lower(?)
            """;

    /** Та же строка, но с текущими name/description/visibility — нужна сервису для PATCH-слияния под блокировкой. */
    static final String MANAGE_ROW = """
            SELECT id, slug, name, description, owner_id, visibility, hidden_at, deleted_at
            FROM groups WHERE id = ?
            """;

    /** Безопасные публичные поля, которые допустимо держать в коротком кэше (TASK-034). */
    static final String PUBLIC_FIELDS = """
            SELECT description, avatar_media_id, cover_media_id FROM groups WHERE id = ?
            """;

    static final String UPDATE_FIELDS = """
            UPDATE groups SET name = ?, description = ?, visibility = ?, version = version + 1, updated_at = now()
            WHERE id = ?
            """;

    static final String SOFT_DELETE = """
            UPDATE groups SET deleted_at = now(), version = version + 1, updated_at = now()
            WHERE id = ? AND deleted_at IS NULL
            """;

    static final String SET_AVATAR = """
            UPDATE groups SET avatar_media_id = ?, version = version + 1, updated_at = now() WHERE id = ?
            """;

    static final String SET_COVER = """
            UPDATE groups SET cover_media_id = ?, version = version + 1, updated_at = now() WHERE id = ?
            """;

    static final String ROLE_OF = """
            SELECT role FROM group_members WHERE group_id = ? AND user_id = ?
            """;

    static final String MEMBER_COUNT = """
            SELECT count(*) FROM group_members WHERE group_id = ?
            """;

    /** Публичное вступление. Приватные сообщества принимают участников только через заявку или приглашение. */
    static final String JOIN_MEMBER = """
            INSERT INTO group_members (group_id, user_id, role, joined_at)
            VALUES (?, ?, 'MEMBER', now())
            """;

    static final String DELETE_MEMBER = """
            DELETE FROM group_members WHERE group_id = ? AND user_id = ?
            """;

    static final String SET_MEMBER_ROLE = """
            UPDATE group_members SET role = ? WHERE group_id = ? AND user_id = ?
            """;

    static final String SET_OWNER = """
            UPDATE groups SET owner_id = ?, version = version + 1, updated_at = now() WHERE id = ?
            """;

    static final String LIST_MEMBERS = """
            SELECT u.id, u.username, p.display_name, m.role
            FROM group_members m
            JOIN users u ON u.id = m.user_id
            JOIN user_profiles p ON p.user_id = u.id
            WHERE m.group_id = ?
            ORDER BY m.joined_at, u.id
            """;

    static final String CATALOG_FIRST = """
            SELECT id, slug, name, avatar_media_id, created_at
            FROM groups
            WHERE visibility = 'PUBLIC' AND hidden_at IS NULL AND deleted_at IS NULL
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """;

    static final String CATALOG_AFTER = """
            SELECT id, slug, name, avatar_media_id, created_at
            FROM groups
            WHERE visibility = 'PUBLIC' AND hidden_at IS NULL AND deleted_at IS NULL
              AND (created_at, id) < (?, ?)
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """;

    static final String MY_GROUPS = """
            SELECT g.id, g.slug, g.name, g.visibility, g.avatar_media_id
            FROM group_members m
            JOIN groups g ON g.id = m.group_id
            WHERE m.user_id = ? AND g.deleted_at IS NULL
            ORDER BY g.name
            """;
}
