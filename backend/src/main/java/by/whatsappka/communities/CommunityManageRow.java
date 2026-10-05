package by.whatsappka.communities;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/** Строка сообщества с текущими name/description — читается сервисом под блокировкой перед PATCH-слиянием. */
record CommunityManageRow(
        UUID id, String slug, String name, String description, UUID ownerId,
        String visibility, Instant hiddenAt, Instant deletedAt
) {

    static final RowMapper<CommunityManageRow> MAPPER = (rs, n) -> new CommunityManageRow(
            UUID.fromString(rs.getString("id")),
            rs.getString("slug"),
            rs.getString("name"),
            rs.getString("description"),
            UUID.fromString(rs.getString("owner_id")),
            rs.getString("visibility"),
            rs.getTimestamp("hidden_at") == null ? null : rs.getTimestamp("hidden_at").toInstant(),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant());

    boolean isDeleted() {
        return deletedAt != null;
    }
}
