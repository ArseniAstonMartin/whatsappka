package by.whatsappka.content;

/**
 * Общий фрагмент видимости публикации по группе (FR-04, FR-12): личный пост виден всегда, групповой —
 * только если группа открыта либо зритель в ней состоит. Публикация/удаление/черновик проверяются
 * отдельно условием {@code status = 'PUBLISHED' AND deleted_at IS NULL} в самом запросе.
 *
 * <p>Блокировки автора в это условие не входят — их проверяет лента (TASK-045), где у видимости
 * появляется получатель-подписчик, а не только зритель группы.
 */
final class PostVisibilitySql {

    private PostVisibilitySql() {
    }

    /** Требует параметр viewerId дважды (owner_id, user_id в group_members) там, где подставлен. */
    static final String GROUP_VISIBLE_TO_VIEWER = """
            (p.group_id IS NULL OR EXISTS (
                SELECT 1 FROM groups g
                WHERE g.id = p.group_id AND g.deleted_at IS NULL
                  AND (g.visibility = 'PUBLIC' OR g.owner_id = ? OR EXISTS (
                      SELECT 1 FROM group_members m WHERE m.group_id = g.id AND m.user_id = ?
                  ))
            ))
            """;
}
