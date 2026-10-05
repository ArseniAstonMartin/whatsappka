package by.whatsappka.social;

import by.whatsappka.platform.web.CursorPage;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import by.whatsappka.platform.web.PageSize;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Списки связей и счётчики. Счётчики считаются из таблицы связей, а не хранятся отдельно. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FollowQueries {

    public record UserSummary(UUID id, String username, String displayName) {
    }

    public record Relations(long followers, long following, boolean followedByViewer, boolean self) {
    }

    private final JdbcTemplate jdbc;
    private final SocialRelations relations;

    public FollowQueries(JdbcTemplate jdbc, SocialRelations relations) {
        this.jdbc = jdbc;
        this.relations = relations;
    }

    static final RowMapper<Cursors.Row> ROW_MAPPER = (rs, n) -> new Cursors.Row(
            UUID.fromString(rs.getString("id")),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getTimestamp("created_at").toInstant());

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> followers(UUID viewerId, UUID targetId, String cursor, int limit) {
        relations.requireVisibleTo(viewerId, targetId);
        return Cursors.page(cursor, PageSize.limit(limit), (key, size) -> key == null
                ? jdbc.query(SocialSql.FOLLOWERS_FIRST, ROW_MAPPER, targetId, size)
                : jdbc.query(SocialSql.FOLLOWERS_AFTER, ROW_MAPPER, targetId, Timestamp.from(key.at()), key.id(), size));
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> following(UUID viewerId, UUID targetId, String cursor, int limit) {
        relations.requireVisibleTo(viewerId, targetId);
        return Cursors.page(cursor, PageSize.limit(limit), (key, size) -> key == null
                ? jdbc.query(SocialSql.FOLLOWING_FIRST, ROW_MAPPER, targetId, size)
                : jdbc.query(SocialSql.FOLLOWING_AFTER, ROW_MAPPER, targetId, Timestamp.from(key.at()), key.id(), size));
    }

    @Transactional(readOnly = true)
    public Relations relations(UUID viewerId, UUID targetId) {
        relations.requireVisibleTo(viewerId, targetId);
        long followers = jdbc.queryForObject(SocialSql.COUNT_FOLLOWERS, Long.class, targetId);
        long following = jdbc.queryForObject(SocialSql.COUNT_FOLLOWING, Long.class, targetId);
        Boolean followed = jdbc.queryForObject(SocialSql.IS_FOLLOWING, Boolean.class, viewerId, targetId);
        return new Relations(followers, following, Boolean.TRUE.equals(followed), viewerId.equals(targetId));
    }

}
