package by.whatsappka.search;

/**
 * SQL поиска постов и хештегов. Видимость проверяется до текста, сниппета и счётчиков: всё, что не видно зрителю,
 * в запросе даже не читается. Параметры видимости — viewer, viewer, viewer, viewer по порядку.
 */
final class PostSearchSql {

    private PostSearchSql() {
    }

    /** Опубликованный пост у активного автора, без блокировок в обе стороны, в доступной группе. */
    private static final String VISIBLE_POST = """
            p.status = 'PUBLISHED' AND p.deleted_at IS NULL
            AND a.status = 'ACTIVE'
            AND NOT EXISTS (SELECT 1 FROM user_blocks b
                            WHERE (b.blocker_id = ? AND b.blocked_id = p.author_id)
                               OR (b.blocker_id = p.author_id AND b.blocked_id = ?))
            AND (p.group_id IS NULL OR EXISTS (
                SELECT 1 FROM groups g
                WHERE g.id = p.group_id AND g.deleted_at IS NULL
                  AND ((g.visibility = 'PUBLIC' AND g.hidden_at IS NULL)
                       OR g.owner_id = ?
                       OR EXISTS (SELECT 1 FROM group_members m WHERE m.group_id = g.id AND m.user_id = ?))))
            """;

    /** Параметры: viewer x4, contains, [cursor: at, id], limit. */
    static final String POSTS_FIRST = """
            SELECT p.id, p.author_id, a.username, pr.display_name, p.published_at,
                   left(p.body, 161) AS snippet_source, char_length(p.body) AS body_length
            FROM posts p
            JOIN users a ON a.id = p.author_id
            JOIN user_profiles pr ON pr.user_id = a.id
            WHERE p.body IS NOT NULL AND """ + VISIBLE_POST + """
              AND whatsappka_search_norm(p.body) LIKE ? ESCAPE '!'
            ORDER BY p.published_at DESC, p.id DESC
            LIMIT ?
            """;

    static final String POSTS_AFTER = """
            SELECT p.id, p.author_id, a.username, pr.display_name, p.published_at,
                   left(p.body, 161) AS snippet_source, char_length(p.body) AS body_length
            FROM posts p
            JOIN users a ON a.id = p.author_id
            JOIN user_profiles pr ON pr.user_id = a.id
            WHERE p.body IS NOT NULL AND """ + VISIBLE_POST + """
              AND whatsappka_search_norm(p.body) LIKE ? ESCAPE '!'
              AND (p.published_at, p.id) < (CAST(? AS timestamptz), CAST(? AS uuid))
            ORDER BY p.published_at DESC, p.id DESC
            LIMIT ?
            """;

    /**
     * Популярность тега — число видимых зрителю постов с ним. Теги без видимых постов не выдаются.
     * Параметры: viewer x4, contains, limit.
     */
    static final String HASHTAGS = """
            SELECT h.normalized_name, h.display_name, count(*) AS popularity
            FROM hashtags h
            JOIN post_hashtags ph ON ph.hashtag_id = h.id
            JOIN posts p ON p.id = ph.post_id
            JOIN users a ON a.id = p.author_id
            WHERE """ + VISIBLE_POST + """
              AND whatsappka_search_norm(h.normalized_name) LIKE ? ESCAPE '!'
            GROUP BY h.id, h.normalized_name, h.display_name
            ORDER BY popularity DESC, h.normalized_name, h.id
            LIMIT ?
            """;
}
