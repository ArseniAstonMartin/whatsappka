package by.whatsappka.content;

import by.whatsappka.media.access.MediaLinkResolver;
import by.whatsappka.media.access.MediaLinkType;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Изображение публикации видно так же, как сама публикация: черновик и расписание — только автору
 * (он и так владелец файла, этот резолвер закрывает доступ всем остальным). Видимость опубликованной
 * записи резолвер получит вместе с лентой и правилами групп (TASK-042+).
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
        List<UUID> authors = jdbc.query(
                "SELECT author_id FROM posts WHERE id = ? AND deleted_at IS NULL",
                (rs, n) -> UUID.fromString(rs.getString("author_id")), linkId);
        return !authors.isEmpty() && authors.get(0).equals(viewerId);
    }
}
