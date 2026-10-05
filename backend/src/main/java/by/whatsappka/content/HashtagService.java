package by.whatsappka.content;

import by.whatsappka.content.PostListingSupport.Keyset;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Список публикаций по хештегу (TASK-044). Видимость — {@link PostVisibilitySql}: та же общая
 * политика, которую переиспользуют лента, профиль и группа (TASK-045).
 */
@Service
public class HashtagService {

    private final JdbcTemplate jdbc;

    public HashtagService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CursorPage<PostSummaryPublic> posts(UUID viewerId, String rawTag, String cursor, Integer limit) {
        String tag = PostRules.normalizeHashtag(rawTag);
        int size = PageSize.limit(limit);
        Keyset key = PostListingSupport.decode(cursor);
        String sql = "SELECT p.id, p.body, p.author_id, p.group_id, p.published_at FROM posts p "
                + "JOIN post_hashtags ph ON ph.post_id = p.id "
                + "JOIN hashtags h ON h.id = ph.hashtag_id "
                + "WHERE h.normalized_name = ? AND p.status = 'PUBLISHED' AND p.deleted_at IS NULL "
                + "AND " + PostVisibilitySql.GROUP_VISIBLE_TO_VIEWER
                + (key == null ? "" : "AND (p.published_at, p.id) < (?, ?) ")
                + "ORDER BY p.published_at DESC, p.id DESC LIMIT ?";
        List<PostSummaryPublic> rows = key == null
                ? jdbc.query(sql, PostListingSupport.ROW_MAPPER, tag, viewerId, viewerId, size + 1)
                : jdbc.query(sql, PostListingSupport.ROW_MAPPER, tag, viewerId, viewerId, Timestamp.from(key.at()), key.id(), size + 1);
        boolean more = rows.size() > size;
        List<PostSummaryPublic> items = more ? rows.subList(0, size) : rows;
        String next = more ? PostListingSupport.encode(items.get(items.size() - 1).publishedAt(), items.get(items.size() - 1).id()) : null;
        return new CursorPage<>(items, next, more);
    }
}
