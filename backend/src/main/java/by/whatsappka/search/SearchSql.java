package by.whatsappka.search;

/**
 * SQL поиска. Совпадение идёт по whatsappka_search_norm (V27), шаблоны экранируются символом «!».
 * Пара параметров для каждого места: префикс (ранг 0) и подстрока (отбор).
 */
final class SearchSql {

    private SearchSql() {
    }

    /** Аккаунт активен, сам зритель и любая блокировка в обе стороны исключаются. */
    private static final String USER_HITS = """
            WITH hits AS (
                SELECT u.id, u.username, p.display_name,
                       CASE WHEN whatsappka_search_norm(u.username) LIKE ? ESCAPE '!' THEN 0
                            WHEN whatsappka_search_norm(p.display_name) LIKE ? ESCAPE '!' THEN 1
                            ELSE 2 END AS hit_rank
                FROM users u
                JOIN user_profiles p ON p.user_id = u.id
                WHERE u.status = 'ACTIVE' AND u.id <> ?
                  AND (whatsappka_search_norm(u.username) LIKE ? ESCAPE '!'
                       OR whatsappka_search_norm(p.display_name) LIKE ? ESCAPE '!')
                  AND NOT EXISTS (SELECT 1 FROM user_blocks b
                                  WHERE (b.blocker_id = ? AND b.blocked_id = u.id)
                                     OR (b.blocker_id = u.id AND b.blocked_id = ?))
            )
            """;

    static final String USERS_FIRST = USER_HITS + """
            SELECT id, username, display_name, hit_rank FROM hits
            ORDER BY hit_rank, username, id
            LIMIT ?
            """;

    static final String USERS_AFTER = USER_HITS + """
            SELECT id, username, display_name, hit_rank FROM hits
            WHERE (hit_rank, username, id) > (CAST(? AS int), CAST(? AS text), CAST(? AS uuid))
            ORDER BY hit_rank, username, id
            LIMIT ?
            """;

    /**
     * Группы: удалённые и скрытые исключены. Приватная группа совпадает только по названию и slug —
     * описание учитывается лишь у публичной. Аватар приватной не отдаётся.
     */
    private static final String GROUP_HITS = """
            WITH hits AS (
                SELECT g.id, g.slug, g.name, g.visibility,
                       CASE WHEN g.visibility = 'PUBLIC' THEN g.avatar_media_id END AS avatar_media_id,
                       CASE WHEN whatsappka_search_norm(g.name) LIKE ? ESCAPE '!' THEN 0
                            WHEN whatsappka_search_norm(g.slug) LIKE ? ESCAPE '!' THEN 1
                            WHEN whatsappka_search_norm(g.name) LIKE ? ESCAPE '!'
                              OR whatsappka_search_norm(g.slug) LIKE ? ESCAPE '!' THEN 2
                            ELSE 3 END AS hit_rank
                FROM groups g
                WHERE g.deleted_at IS NULL AND g.hidden_at IS NULL
                  AND (whatsappka_search_norm(g.name) LIKE ? ESCAPE '!'
                       OR whatsappka_search_norm(g.slug) LIKE ? ESCAPE '!'
                       OR (g.visibility = 'PUBLIC' AND whatsappka_search_norm(g.description) LIKE ? ESCAPE '!'))
            )
            """;

    static final String GROUPS_FIRST = GROUP_HITS + """
            SELECT id, slug, name, visibility, avatar_media_id, hit_rank FROM hits
            ORDER BY hit_rank, name, id
            LIMIT ?
            """;

    static final String GROUPS_AFTER = GROUP_HITS + """
            SELECT id, slug, name, visibility, avatar_media_id, hit_rank FROM hits
            WHERE (hit_rank, name, id) > (CAST(? AS int), CAST(? AS text), CAST(? AS uuid))
            ORDER BY hit_rank, name, id
            LIMIT ?
            """;
}
