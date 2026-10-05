package by.whatsappka.media.access;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Решение о доступе к файлу. Владелец видит свой файл; остальные — только если хотя бы одна привязка
 * разрешена резолвером её типа. Отсутствие резолвера означает отказ.
 */
@Component
public class MediaAccessPolicy {

    private final JdbcTemplate jdbc;
    private final Map<MediaLinkType, MediaLinkResolver> resolvers;

    public MediaAccessPolicy(JdbcTemplate jdbc, List<MediaLinkResolver> resolvers) {
        this.jdbc = jdbc;
        EnumMap<MediaLinkType, MediaLinkResolver> byType = new EnumMap<>(MediaLinkType.class);
        for (MediaLinkResolver resolver : resolvers) {
            if (byType.putIfAbsent(resolver.type(), resolver) != null) {
                throw new IllegalStateException("Два резолвера для типа связи " + resolver.type());
            }
        }
        this.resolvers = byType;
    }

    public boolean canView(UUID viewerId, UUID mediaId, UUID ownerId) {
        if (viewerId.equals(ownerId)) {
            return true;
        }
        List<Link> links = jdbc.query(
                "SELECT link_type, link_id FROM media_links WHERE media_id = ?",
                (rs, row) -> new Link(MediaLinkType.valueOf(rs.getString("link_type")), UUID.fromString(rs.getString("link_id"))),
                mediaId);
        for (Link link : links) {
            MediaLinkResolver resolver = resolvers.get(link.type());
            if (resolver != null && resolver.canView(viewerId, link.id())) {
                return true;
            }
        }
        return false;
    }

    private record Link(MediaLinkType type, UUID id) {
    }
}
