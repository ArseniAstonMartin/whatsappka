package by.whatsappka.communities;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Строка сообщества, достаточная для решения о доступе и для каталога/ограниченного вида.
 * Описание, аватар и обложка сюда не входят: они читаются отдельно (кэшируемая часть, TASK-034).
 */
record CommunityRow(UUID id, String slug, String name, UUID ownerId, String visibility, Instant hiddenAt, Instant deletedAt) {

    static final RowMapper<CommunityRow> MAPPER = (rs, n) -> new CommunityRow(
            UUID.fromString(rs.getString("id")),
            rs.getString("slug"),
            rs.getString("name"),
            UUID.fromString(rs.getString("owner_id")),
            rs.getString("visibility"),
            rs.getTimestamp("hidden_at") == null ? null : rs.getTimestamp("hidden_at").toInstant(),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant());

    boolean isPublic() {
        return "PUBLIC".equals(visibility);
    }

    boolean isHidden() {
        return hiddenAt != null;
    }

    boolean isDeleted() {
        return deletedAt != null;
    }
}
