package by.whatsappka.messaging;

import by.whatsappka.platform.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Состав группы: виден только участникам. Владелец выводится из чата, а не из строки членства. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GroupQueries {

    public record Member(UUID id, String username, String displayName, String role) {
    }

    private final JdbcTemplate jdbc;
    private final GroupService groups;

    public GroupQueries(JdbcTemplate jdbc, GroupService groups) {
        this.jdbc = jdbc;
        this.groups = groups;
    }

    @Transactional(readOnly = true)
    public List<Member> members(UUID viewerId, UUID conversationId) {
        if (groups.activeRole(conversationId, viewerId) == null) {
            throw ApiException.notFound();
        }
        UUID owner = jdbc.queryForObject("SELECT owner_id FROM conversations WHERE id = ?", UUID.class, conversationId);
        return jdbc.query(GroupSql.LIST_MEMBERS, (rs, n) -> {
            UUID id = UUID.fromString(rs.getString("id"));
            String role = id.equals(owner) ? "OWNER" : rs.getString("role");
            return new Member(id, rs.getString("username"), rs.getString("display_name"), role);
        }, conversationId);
    }
}
