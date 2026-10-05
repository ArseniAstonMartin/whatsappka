package by.whatsappka.communities;

import by.whatsappka.communities.CommunityRules.Role;
import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Заявки на вступление в приватное сообщество (TASK-037). Принятие атомарно добавляет членство;
 * решение — только OWNER/ADMIN. Открытое сообщество принимает участников напрямую (TASK-036),
 * без заявки.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommunityJoinRequestService {

    static final Duration REAPPLY_COOLDOWN = Duration.ofHours(24);

    static final String EVENT_CREATED = "group.join_request_created";
    static final String EVENT_ACCEPTED = "group.join_request_accepted";
    static final String EVENT_REJECTED = "group.join_request_rejected";

    private final JdbcTemplate jdbc;
    private final OutboxWriter outbox;
    private final Clock clock;

    public CommunityJoinRequestService(JdbcTemplate jdbc, OutboxWriter outbox, Clock clock) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.clock = clock;
    }

    public record Request(UUID id, UUID requesterId, String username, String displayName, Instant createdAt) {
    }

    @Transactional
    public UUID request(UUID groupId, UUID requesterId) {
        lock(groupId);
        CommunityRow group = requireRow(groupId);
        if (group.isHidden()) {
            throw ApiException.notFound();
        }
        if (group.isPublic()) {
            throw new ApiException(HttpStatus.CONFLICT, "public_group",
                    "Открытое сообщество не требует заявки, доступно прямое вступление", List.of(), null);
        }
        if (group.ownerId().equals(requesterId) || roleOf(group, requesterId) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "already_member", "Вы уже состоите в сообществе", List.of(), null);
        }
        Instant now = clock.instant();
        Instant cooldownUntil = rejectedCooldownUntil(groupId, requesterId);
        if (cooldownUntil != null && cooldownUntil.isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "join_request_cooldown",
                    "Повторная заявка возможна не раньше чем через 24 часа после отказа", List.of(), null);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO group_join_requests (id, group_id, requester_id, status, created_at) "
                    + "VALUES (?, ?, ?, 'PENDING', ?)", id, groupId, requesterId, Timestamp.from(now));
        } catch (DuplicateKeyException duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "request_pending", "Заявка уже ожидает решения", List.of(), null);
        }
        outbox.record("group", groupId, EVENT_CREATED,
                Map.of("groupId", groupId.toString(), "requestId", id.toString(), "requesterId", requesterId.toString()));
        return id;
    }

    @Transactional
    public void cancel(UUID groupId, UUID requesterId, UUID requestId) {
        JoinRequestRow row = loadInGroup(groupId, requestId);
        if (!row.requesterId().equals(requesterId)) {
            throw ApiException.notFound();
        }
        if (!"PENDING".equals(row.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "request_closed", "Заявка уже закрыта", List.of(), null);
        }
        setStatus(requestId, "CANCELLED", null, clock.instant());
    }

    /** Повторное принятие уже принятой заявки — идемпотентный no-op. */
    @Transactional
    public void accept(UUID groupId, UUID actorId, UUID requestId) {
        lock(groupId);
        CommunityRow group = requireRow(groupId);
        requireAdminOrOwner(group, actorId);
        JoinRequestRow row = loadInGroup(groupId, requestId);
        if ("ACCEPTED".equals(row.status())) {
            return;
        }
        if (!"PENDING".equals(row.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "request_closed", "Заявка уже закрыта", List.of(), null);
        }
        try {
            jdbc.update(CommunitySql.JOIN_MEMBER, groupId, row.requesterId());
        } catch (DuplicateKeyException alreadyMember) {
            // Участник мог появиться другим путём между подачей заявки и решением — всё равно закрываем как принятую.
        }
        setStatus(requestId, "ACCEPTED", actorId, clock.instant());
        outbox.record("group", groupId, EVENT_ACCEPTED,
                Map.of("groupId", groupId.toString(), "requestId", requestId.toString(), "requesterId", row.requesterId().toString()));
    }

    /** Повторное отклонение уже отклонённой заявки — идемпотентный no-op. */
    @Transactional
    public void reject(UUID groupId, UUID actorId, UUID requestId) {
        lock(groupId);
        CommunityRow group = requireRow(groupId);
        requireAdminOrOwner(group, actorId);
        JoinRequestRow row = loadInGroup(groupId, requestId);
        if ("REJECTED".equals(row.status())) {
            return;
        }
        if (!"PENDING".equals(row.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "request_closed", "Заявка уже закрыта", List.of(), null);
        }
        setStatus(requestId, "REJECTED", actorId, clock.instant());
        outbox.record("group", groupId, EVENT_REJECTED,
                Map.of("groupId", groupId.toString(), "requestId", requestId.toString(), "requesterId", row.requesterId().toString()));
    }

    /** Список ожидающих заявок виден только OWNER/ADMIN; обычным участникам и посторонним — нет. */
    @Transactional(readOnly = true)
    public List<Request> pending(UUID groupId, UUID viewerId) {
        CommunityRow group = requireRow(groupId);
        requireAdminOrOwner(group, viewerId);
        return jdbc.query(
                "SELECT r.id, r.requester_id, u.username, p.display_name, r.created_at FROM group_join_requests r "
                        + "JOIN users u ON u.id = r.requester_id "
                        + "JOIN user_profiles p ON p.user_id = u.id "
                        + "WHERE r.group_id = ? AND r.status = 'PENDING' ORDER BY r.created_at, r.id",
                (rs, n) -> new Request(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("requester_id")),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getTimestamp("created_at").toInstant()),
                groupId);
    }

    /** Не null только если последняя заявка пары была отклонена — тогда это момент, с которого снова можно подавать. */
    private Instant rejectedCooldownUntil(UUID groupId, UUID requesterId) {
        List<Instant> rows = jdbc.query(
                "SELECT status, responded_at FROM group_join_requests WHERE group_id = ? AND requester_id = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                (rs, n) -> "REJECTED".equals(rs.getString("status")) && rs.getTimestamp("responded_at") != null
                        ? rs.getTimestamp("responded_at").toInstant().plus(REAPPLY_COOLDOWN)
                        : null,
                groupId, requesterId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record JoinRequestRow(UUID requesterId, String status) {
    }

    private JoinRequestRow loadInGroup(UUID groupId, UUID requestId) {
        List<JoinRequestRow> rows = jdbc.query(
                "SELECT requester_id, status FROM group_join_requests WHERE id = ? AND group_id = ?",
                (rs, n) -> new JoinRequestRow(UUID.fromString(rs.getString("requester_id")), rs.getString("status")),
                requestId, groupId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        return rows.get(0);
    }

    private void setStatus(UUID requestId, String status, UUID decidedBy, Instant now) {
        jdbc.update("UPDATE group_join_requests SET status = ?, decided_by = ?, responded_at = ? WHERE id = ?",
                status, decidedBy, Timestamp.from(now), requestId);
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

    private Role roleOf(CommunityRow group, UUID userId) {
        if (group.ownerId().equals(userId)) {
            return Role.OWNER;
        }
        List<String> roles = jdbc.queryForList(CommunitySql.ROLE_OF, String.class, group.id(), userId);
        return roles.isEmpty() ? null : Role.valueOf(roles.get(0));
    }

    private void requireAdminOrOwner(CommunityRow group, UUID actorId) {
        Role role = roleOf(group, actorId);
        if (role != Role.OWNER && role != Role.ADMIN) {
            throw ApiException.forbidden();
        }
    }
}
