package by.whatsappka.content;

import by.whatsappka.content.PostListingSupport.Keyset;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Лента подписок, посты профиля и посты группы (TASK-045). Проверка прав всегда идёт по актуальным
 * данным: PublicFieldsCache (TASK-034) кэширует только безопасные описательные поля, не авторизацию.
 */
@Service
public class FeedService {

    /** Активный автор, которого зритель не заблокировал и который не заблокировал зрителя. */
    private static final String AUTHOR_VISIBLE = """
            u.status = 'ACTIVE' AND NOT EXISTS (
                SELECT 1 FROM user_blocks b
                WHERE (b.blocker_id = ? AND b.blocked_id = p.author_id)
                   OR (b.blocker_id = p.author_id AND b.blocked_id = ?)
            )
            """;

    private final JdbcTemplate jdbc;

    public FeedService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Свои посты, личные посты подписок и посты групп читателя — без дублей, каждый пост проходит
     * один SELECT. Групповой пост подписанного автора добавляется только через собственное членство
     * читателя в этой же группе, а не через подписку на автора.
     */
    @Transactional(readOnly = true)
    public CursorPage<PostSummaryPublic> feed(UUID viewerId, String cursor, Integer limit) {
        String source = """
                (p.author_id = ?
                 OR (p.group_id IS NULL AND EXISTS (
                     SELECT 1 FROM follows f WHERE f.follower_id = ? AND f.followee_id = p.author_id
                 ))
                 OR (p.group_id IS NOT NULL AND EXISTS (
                     SELECT 1 FROM groups g WHERE g.id = p.group_id AND g.deleted_at IS NULL
                     AND (g.owner_id = ? OR EXISTS (SELECT 1 FROM group_members m WHERE m.group_id = g.id AND m.user_id = ?))
                 )))
                """;
        return page(viewerId, source, List.of(viewerId, viewerId, viewerId, viewerId), cursor, limit);
    }

    /** Публикации автора, доступные зрителю: личные всегда, групповые — только если группа видна зрителю. */
    @Transactional(readOnly = true)
    public CursorPage<PostSummaryPublic> profilePosts(UUID viewerId, UUID authorId, String cursor, Integer limit) {
        String source = "(p.author_id = ? AND " + PostVisibilitySql.GROUP_VISIBLE_TO_VIEWER + ")";
        return page(viewerId, source, List.of(authorId, viewerId, viewerId), cursor, limit);
    }

    /** Публикации конкретной группы. Группа должна быть видна зрителю (открыта либо он в ней состоит). */
    @Transactional(readOnly = true)
    public CursorPage<PostSummaryPublic> groupPosts(UUID viewerId, UUID groupId, String cursor, Integer limit) {
        requireGroupVisible(groupId, viewerId);
        return page(viewerId, "p.group_id = ?", List.of(groupId), cursor, limit);
    }

    private CursorPage<PostSummaryPublic> page(
            UUID viewerId, String sourceCondition, List<Object> sourceParams, String cursor, Integer limit
    ) {
        int size = PageSize.limit(limit);
        Keyset key = PostListingSupport.decode(cursor);
        String sql = "SELECT p.id, p.body, p.author_id, p.group_id, p.published_at FROM posts p "
                + "JOIN users u ON u.id = p.author_id "
                + "WHERE p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND " + AUTHOR_VISIBLE
                + "AND " + sourceCondition + " "
                + (key == null ? "" : "AND (p.published_at, p.id) < (?, ?) ")
                + "ORDER BY p.published_at DESC, p.id DESC LIMIT ?";
        List<Object> params = new ArrayList<>();
        params.add(viewerId);
        params.add(viewerId);
        params.addAll(sourceParams);
        if (key != null) {
            params.add(Timestamp.from(key.at()));
            params.add(key.id());
        }
        params.add(size + 1);
        List<PostSummaryPublic> rows = jdbc.query(sql, PostListingSupport.ROW_MAPPER, params.toArray());
        boolean more = rows.size() > size;
        List<PostSummaryPublic> items = more ? rows.subList(0, size) : rows;
        String next = more
                ? PostListingSupport.encode(items.get(items.size() - 1).publishedAt(), items.get(items.size() - 1).id())
                : null;
        return new CursorPage<>(items, next, more);
    }

    private void requireGroupVisible(UUID groupId, UUID viewerId) {
        List<GroupRow> rows = jdbc.query(
                "SELECT visibility, owner_id, hidden_at, deleted_at FROM groups WHERE id = ?",
                (rs, n) -> new GroupRow(
                        rs.getString("visibility"),
                        UUID.fromString(rs.getString("owner_id")),
                        rs.getTimestamp("hidden_at") != null,
                        rs.getTimestamp("deleted_at") != null),
                groupId);
        if (rows.isEmpty() || rows.get(0).deleted()) {
            throw ApiException.notFound();
        }
        GroupRow row = rows.get(0);
        boolean member = row.ownerId().equals(viewerId) || Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT count(*) > 0 FROM group_members WHERE group_id = ? AND user_id = ?", Boolean.class, groupId, viewerId));
        if ((row.hidden() || !"PUBLIC".equals(row.visibility())) && !member) {
            throw ApiException.notFound();
        }
    }

    private record GroupRow(String visibility, UUID ownerId, boolean hidden, boolean deleted) {
    }
}
