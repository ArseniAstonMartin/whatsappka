package by.whatsappka.communities;

import by.whatsappka.communities.CommunityRules.Role;
import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Членство и роли участников сообщества (TASK-036). Публичное вступление и выход доступны напрямую;
 * приватные сообщества принимают участников только через заявку (TASK-037) или приглашение (TASK-038).
 * Изменения состава идут под той же advisory-блокировкой, что настройки сообщества ({@link CommunitySql#LOCK_GROUP}).
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommunityMembershipService {

    static final String EVENT_MEMBER_JOINED = "group.member_joined";
    static final String EVENT_MEMBER_LEFT = "group.member_left";
    static final String EVENT_MEMBER_REMOVED = "group.member_removed";
    static final String EVENT_ROLE_CHANGED = "group.role_changed";
    static final String EVENT_OWNER_TRANSFERRED = "group.owner_transferred";

    private final JdbcTemplate jdbc;
    private final OutboxWriter outbox;

    public CommunityMembershipService(JdbcTemplate jdbc, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    public record Member(UUID id, String username, String displayName, String role) {
    }

    @Transactional
    public void join(UUID groupId, UUID userId) {
        lock(groupId);
        CommunityRow group = requireRow(groupId);
        if (group.ownerId().equals(userId) || roleOf(group, userId) != null) {
            return;
        }
        if (group.isHidden()) {
            throw ApiException.notFound();
        }
        if (!group.isPublic()) {
            throw new ApiException(HttpStatus.CONFLICT, "private_group",
                    "Сообщество приватно, нужна заявка на вступление", List.of(), null);
        }
        jdbc.update(CommunitySql.JOIN_MEMBER, groupId, userId);
        outbox.record("group", groupId, EVENT_MEMBER_JOINED,
                Map.of("groupId", groupId.toString(), "userId", userId.toString()));
    }

    @Transactional
    public void leave(UUID groupId, UUID userId) {
        lock(groupId);
        CommunityRow group = requireRow(groupId);
        Role role = requireMember(group, userId);
        if (!CommunityRules.canLeave(role)) {
            throw new ApiException(HttpStatus.CONFLICT, "owner_must_transfer",
                    "Владелец не может выйти без передачи владения или удаления сообщества", List.of(), null);
        }
        jdbc.update(CommunitySql.DELETE_MEMBER, groupId, userId);
        outbox.record("group", groupId, EVENT_MEMBER_LEFT,
                Map.of("groupId", groupId.toString(), "userId", userId.toString()));
    }

    @Transactional
    public void remove(UUID groupId, UUID actorId, UUID targetId) {
        lock(groupId);
        CommunityRow group = requireRow(groupId);
        Role actor = requireMember(group, actorId);
        Role target = requireMember(group, targetId);
        if (!CommunityRules.canRemove(actor, target)) {
            throw ApiException.forbidden();
        }
        jdbc.update(CommunitySql.DELETE_MEMBER, groupId, targetId);
        outbox.record("group", groupId, EVENT_MEMBER_REMOVED,
                Map.of("groupId", groupId.toString(), "actorId", actorId.toString(), "userId", targetId.toString()));
    }

    @Transactional
    public void setRole(UUID groupId, UUID actorId, UUID targetId, String rawRole) {
        lock(groupId);
        CommunityRow group = requireRow(groupId);
        Role actor = requireMember(group, actorId);
        Role target = requireMember(group, targetId);
        Role role = CommunityRules.parseAssignableRole(rawRole);
        if (!CommunityRules.canSetRole(actor, target)) {
            throw ApiException.forbidden();
        }
        jdbc.update(CommunitySql.SET_MEMBER_ROLE, role.name(), groupId, targetId);
        outbox.record("group", groupId, EVENT_ROLE_CHANGED,
                Map.of("groupId", groupId.toString(), "actorId", actorId.toString(), "userId", targetId.toString()));
    }

    /** Владение переходит атомарно: новый владелец назначен, прежний становится администратором в той же транзакции. */
    @Transactional
    public void transferOwnership(UUID groupId, UUID actorId, UUID targetId) {
        lock(groupId);
        CommunityRow group = requireRow(groupId);
        Role actor = requireMember(group, actorId);
        requireMember(group, targetId);
        if (!CommunityRules.canTransferOwnership(actor) || targetId.equals(actorId)) {
            throw ApiException.forbidden();
        }
        jdbc.update(CommunitySql.SET_OWNER, targetId, groupId);
        jdbc.update(CommunitySql.SET_MEMBER_ROLE, Role.ADMIN.name(), groupId, actorId);
        jdbc.update(CommunitySql.SET_MEMBER_ROLE, Role.ADMIN.name(), groupId, targetId);
        outbox.record("group", groupId, EVENT_OWNER_TRANSFERRED,
                Map.of("groupId", groupId.toString(), "previousOwnerId", actorId.toString(), "ownerId", targetId.toString()));
    }

    /**
     * Состав открытого сообщества виден любому, как и остальные публичные метаданные (TASK-035);
     * приватный и скрытый модерацией состав доступен только участникам — та же проверка, что в {@link CommunityQueries}.
     */
    @Transactional(readOnly = true)
    public List<Member> members(UUID viewerId, UUID groupId) {
        CommunityRow group = requireRow(groupId);
        boolean member = group.ownerId().equals(viewerId) || roleOf(group, viewerId) != null;
        if ((group.isHidden() || !group.isPublic()) && !member) {
            throw ApiException.notFound();
        }
        UUID ownerId = group.ownerId();
        return jdbc.query(CommunitySql.LIST_MEMBERS, (rs, n) -> {
            UUID id = UUID.fromString(rs.getString("id"));
            String role = id.equals(ownerId) ? Role.OWNER.name() : rs.getString("role");
            return new Member(id, rs.getString("username"), rs.getString("display_name"), role);
        }, groupId);
    }

    private void lock(UUID groupId) {
        jdbc.query(CommunitySql.LOCK_GROUP, rs -> { }, groupId);
    }

    private CommunityRow requireRow(UUID groupId) {
        return jdbc.query(CommunitySql.ROW_BY_ID, CommunityRow.MAPPER, groupId).stream()
                .findFirst()
                .filter(row -> !row.isDeleted())
                .orElseThrow(ApiException::notFound);
    }

    private Role requireMember(CommunityRow group, UUID userId) {
        Role role = roleOf(group, userId);
        if (role == null) {
            throw ApiException.notFound();
        }
        return role;
    }

    private Role roleOf(CommunityRow group, UUID userId) {
        if (group.ownerId().equals(userId)) {
            return Role.OWNER;
        }
        List<String> roles = jdbc.queryForList(CommunitySql.ROLE_OF, String.class, group.id(), userId);
        return roles.isEmpty() ? null : Role.valueOf(roles.get(0));
    }
}
