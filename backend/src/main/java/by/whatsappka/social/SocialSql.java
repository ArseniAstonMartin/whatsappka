package by.whatsappka.social;

/** SQL подписок. Константы вынесены, чтобы их можно было проверить отдельно от Spring. */
final class SocialSql {

    private SocialSql() {
    }

    /** Повтор PUT не создаёт дубликата: вставка с конфликтом ничего не меняет. */
    static final String INSERT_FOLLOW = """
            INSERT INTO follows (follower_id, followee_id, created_at)
            VALUES (?, ?, now())
            ON CONFLICT (follower_id, followee_id) DO NOTHING
            """;

    static final String DELETE_FOLLOW = """
            DELETE FROM follows WHERE follower_id = ? AND followee_id = ?
            """;

    static final String IS_FOLLOWING = """
            SELECT EXISTS (SELECT 1 FROM follows WHERE follower_id = ? AND followee_id = ?)
            """;

    static final String COUNT_FOLLOWERS = """
            SELECT count(*) FROM follows f JOIN users u ON u.id = f.follower_id
            WHERE f.followee_id = ? AND u.status = 'ACTIVE'
            """;

    static final String COUNT_FOLLOWING = """
            SELECT count(*) FROM follows f JOIN users u ON u.id = f.followee_id
            WHERE f.follower_id = ? AND u.status = 'ACTIVE'
            """;

    /** Подписчики: первая страница. Неактивные аккаунты в списках не показываются. */
    static final String FOLLOWERS_FIRST = """
            SELECT u.id, u.username, p.display_name, f.created_at
            FROM follows f
            JOIN users u ON u.id = f.follower_id
            JOIN user_profiles p ON p.user_id = u.id
            WHERE f.followee_id = ? AND u.status = 'ACTIVE'
            ORDER BY f.created_at DESC, f.follower_id DESC
            LIMIT ?
            """;

    static final String FOLLOWERS_AFTER = """
            SELECT u.id, u.username, p.display_name, f.created_at
            FROM follows f
            JOIN users u ON u.id = f.follower_id
            JOIN user_profiles p ON p.user_id = u.id
            WHERE f.followee_id = ? AND u.status = 'ACTIVE'
              AND (f.created_at, f.follower_id) < (?, ?)
            ORDER BY f.created_at DESC, f.follower_id DESC
            LIMIT ?
            """;

    static final String FOLLOWING_FIRST = """
            SELECT u.id, u.username, p.display_name, f.created_at
            FROM follows f
            JOIN users u ON u.id = f.followee_id
            JOIN user_profiles p ON p.user_id = u.id
            WHERE f.follower_id = ? AND u.status = 'ACTIVE'
            ORDER BY f.created_at DESC, f.followee_id DESC
            LIMIT ?
            """;

    static final String FOLLOWING_AFTER = """
            SELECT u.id, u.username, p.display_name, f.created_at
            FROM follows f
            JOIN users u ON u.id = f.followee_id
            JOIN user_profiles p ON p.user_id = u.id
            WHERE f.follower_id = ? AND u.status = 'ACTIVE'
              AND (f.created_at, f.followee_id) < (?, ?)
            ORDER BY f.created_at DESC, f.followee_id DESC
            LIMIT ?
            """;

    static final String IS_BLOCKED_EITHER_WAY = """
            SELECT EXISTS (SELECT 1 FROM user_blocks
                           WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?))
            """;

    static final String INSERT_BLOCK = """
            INSERT INTO user_blocks (blocker_id, blocked_id, created_at)
            VALUES (?, ?, now())
            ON CONFLICT (blocker_id, blocked_id) DO NOTHING
            """;

    static final String DELETE_BLOCK = """
            DELETE FROM user_blocks WHERE blocker_id = ? AND blocked_id = ?
            """;

    /** Блокировка удаляет подписки в обе стороны. Вторую строку и старые подписки не восстанавливаем при разблокировке. */
    static final String DELETE_FOLLOWS_BETWEEN = """
            DELETE FROM follows
            WHERE (follower_id = ? AND followee_id = ?) OR (follower_id = ? AND followee_id = ?)
            """;

    static final String BLOCKS_FIRST = """
            SELECT u.id, u.username, p.display_name, b.created_at
            FROM user_blocks b
            JOIN users u ON u.id = b.blocked_id
            JOIN user_profiles p ON p.user_id = u.id
            WHERE b.blocker_id = ?
            ORDER BY b.created_at DESC, b.blocked_id DESC
            LIMIT ?
            """;

    static final String BLOCKS_AFTER = """
            SELECT u.id, u.username, p.display_name, b.created_at
            FROM user_blocks b
            JOIN users u ON u.id = b.blocked_id
            JOIN user_profiles p ON p.user_id = u.id
            WHERE b.blocker_id = ?
              AND (b.created_at, b.blocked_id) < (?, ?)
            ORDER BY b.created_at DESC, b.blocked_id DESC
            LIMIT ?
            """;
}
