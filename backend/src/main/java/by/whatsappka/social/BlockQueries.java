package by.whatsappka.social;

import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Список собственных блокировок. Заблокированный аккаунт виден владельцу списка, даже если он неактивен. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BlockQueries {

    private final JdbcTemplate jdbc;

    public BlockQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CursorPage<FollowQueries.UserSummary> blocks(UUID viewerId, String cursor, int limit) {
        return Cursors.page(cursor, PageSize.limit(limit), (key, size) -> key == null
                ? jdbc.query(SocialSql.BLOCKS_FIRST, FollowQueries.ROW_MAPPER, viewerId, size)
                : jdbc.query(SocialSql.BLOCKS_AFTER, FollowQueries.ROW_MAPPER, viewerId,
                        Timestamp.from(key.at()), key.id(), size));
    }
}
