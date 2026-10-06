package by.whatsappka.content;

import by.whatsappka.media.access.MediaLinkResolver;
import by.whatsappka.media.access.MediaLinkType;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Изображение публикации видно так же, как сама публикация: автору — всегда (включая черновик и расписание),
 * остальным — только опубликованную запись, если автор активен, между сторонами нет блокировки и группа
 * видна зрителю. Те же правила, что у текста записи, поэтому картинка не открывается там, где запись скрыта.
 */
@Component
public class PostMediaResolver implements MediaLinkResolver {

    private final JdbcTemplate jdbc;

    public PostMediaResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MediaLinkType type() {
        return MediaLinkType.POST_ATTACHMENT;
    }

    @Override
    public boolean canView(UUID viewerId, UUID linkId) {
        List<Boolean> visible = jdbc.query(
                "SELECT (p.author_id = ?) OR (p.status = 'PUBLISHED' "
                        + "AND NOT EXISTS (SELECT 1 FROM user_blocks b "
                        + "  WHERE (b.blocker_id = p.author_id AND b.blocked_id = ?) "
                        + "     OR (b.blocker_id = ? AND b.blocked_id = p.author_id)) "
                        + "AND " + PostVisibilitySql.GROUP_VISIBLE_TO_VIEWER + ") AS visible "
                        + "FROM posts p JOIN users u ON u.id = p.author_id "
                        + "WHERE p.id = ? AND p.deleted_at IS NULL AND u.status = 'ACTIVE'",
                (rs, n) -> rs.getBoolean("visible"),
                viewerId, viewerId, viewerId, viewerId, viewerId, linkId);
        return !visible.isEmpty() && visible.get(0);
    }
}
