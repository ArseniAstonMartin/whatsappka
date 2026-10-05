package by.whatsappka.communities;

import by.whatsappka.communities.CommunityRules.Role;
import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.social.SocialRelations;
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
 * Приглашения в сообщество (TASK-038). Приглашать может только OWNER/ADMIN, принять или отклонить —
 * только получатель. Принятие атомарно добавляет членство.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommunityInvitationService {

    static final Duration TTL = Duration.ofDays(7);
    static final String EVENT_CREATED = "group.invitation_created";
    static final String EVENT_ACCEPTED = "group.invitation_accepted";

    private final JdbcTemplate jdbc;
    private final SocialRelations relations;
    private final OutboxWriter outbox;
    private final Clock clock;

    public CommunityInvitationService(JdbcTemplate jdbc, SocialRelations relations, OutboxWriter outbox, Clock clock) {
        this.jdbc = jdbc;
        this.relations = relations;
        this.outbox = outbox;
        this.clock = clock;
    }

    public record PendingView(UUID id, UUID groupId, String groupName, Instant expiresAt) {
    }

    @Transactional
    public UUID invite(UUID viewerId, UUID groupId, UUID inviteeId) {
        if (viewerId.equals(inviteeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "self_invite", "Нельзя пригласить себя", List.of(), null);
        }
        lock(groupId);
        CommunityRow group = requireRow(groupId);
        Role actor = roleOf(group, viewerId);
        if (actor == null) {
            throw ApiException.notFound();
        }
        if (!CommunityRules.canInvite(actor)) {
            throw ApiException.forbidden();
        }
        relations.requireVisibleTo(viewerId, inviteeId);
        if (group.ownerId().equals(inviteeId) || roleOf(group, inviteeId) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "already_member", "Пользователь уже в сообществе", List.of(), null);
        }
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO group_invitations (id, group_id, inviter_id, invitee_id, status, expires_at, created_at) "
                    + "VALUES (?, ?, ?, ?, 'PENDING', ?, ?)",
                    id, groupId, viewerId, inviteeId, Timestamp.from(now.plus(TTL)), Timestamp.from(now));
        } catch (DuplicateKeyException duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "invitation_pending", "Приглашение уже ожидает ответа", List.of(), null);
        }
        outbox.record("group", groupId, EVENT_CREATED, Map.of(
                "invitationId", id.toString(), "groupId", groupId.toString(), "inviteeId", inviteeId.toString()));
        return id;
    }

    /** Истёкшее приглашение помечается EXPIRED и остаётся в таком виде даже при ответе с ошибкой. */
    @Transactional(noRollbackFor = ApiException.class)
    public UUID accept(UUID viewerId, UUID invitationId) {
        Invitation invitation = load(invitationId);
        lock(invitation.groupId());
        Instant now = clock.instant();
        if (!viewerId.equals(invitation.inviteeId())) {
            throw ApiException.notFound();
        }
        if (!"PENDING".equals(invitation.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "invitation_closed", "Приглашение уже закрыто", List.of(), null);
        }
        if (!invitation.expiresAt().isAfter(now)) {
            setStatus(invitationId, "EXPIRED", now);
            throw new ApiException(HttpStatus.CONFLICT, "invitation_expired", "Срок приглашения истёк", List.of(), null);
        }
        CommunityRow group = requireRow(invitation.groupId());
        Role inviterRole = roleOf(group, invitation.inviterId());
        if (inviterRole == null || !CommunityRules.canInvite(inviterRole)) {
            setStatus(invitationId, "REVOKED", now);
            throw new ApiException(HttpStatus.CONFLICT, "inviter_no_longer_allowed",
                    "Пригласивший больше не может приглашать в это сообщество", List.of(), null);
        }
        relations.requireVisibleTo(viewerId, invitation.inviterId());
        try {
            jdbc.update(CommunitySql.JOIN_MEMBER, invitation.groupId(), viewerId);
        } catch (DuplicateKeyException alreadyMember) {
            // Участник мог появиться другим путём между приглашением и ответом.
        }
        setStatus(invitationId, "ACCEPTED", now);
        outbox.record("group", invitation.groupId(), EVENT_ACCEPTED, Map.of(
                "invitationId", invitationId.toString(), "inviteeId", viewerId.toString()));
        return invitation.groupId();
    }

    @Transactional
    public void decline(UUID viewerId, UUID invitationId) {
        Invitation invitation = load(invitationId);
        if (!viewerId.equals(invitation.inviteeId())) {
            throw ApiException.notFound();
        }
        if (!"PENDING".equals(invitation.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "invitation_closed", "Приглашение уже закрыто", List.of(), null);
        }
        setStatus(invitationId, "DECLINED", clock.instant());
    }

    @Transactional
    public void revoke(UUID viewerId, UUID invitationId) {
        Invitation invitation = load(invitationId);
        lock(invitation.groupId());
        CommunityRow group = requireRow(invitation.groupId());
        Role viewerRole = roleOf(group, viewerId);
        boolean canRevoke = viewerId.equals(invitation.inviterId()) || CommunityRules.canInvite(viewerRole);
        if (!canRevoke) {
            throw ApiException.forbidden();
        }
        if (!"PENDING".equals(invitation.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "invitation_closed", "Приглашение уже закрыто", List.of(), null);
        }
        setStatus(invitationId, "REVOKED", clock.instant());
    }

    /** Входящие приглашения получателя, которые ещё можно принять. */
    @Transactional(readOnly = true)
    public List<PendingView> pendingFor(UUID viewerId) {
        return jdbc.query(
                "SELECT i.id, i.group_id, g.name, i.expires_at FROM group_invitations i "
                        + "JOIN groups g ON g.id = i.group_id "
                        + "WHERE i.invitee_id = ? AND i.status = 'PENDING' AND i.expires_at > ? ORDER BY i.created_at DESC, i.id",
                (rs, n) -> new PendingView(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("group_id")),
                        rs.getString("name"),
                        rs.getTimestamp("expires_at").toInstant()),
                viewerId, Timestamp.from(clock.instant()));
    }

    private record Invitation(UUID groupId, UUID inviterId, UUID inviteeId, String status, Instant expiresAt) {
    }

    private Invitation load(UUID invitationId) {
        List<Invitation> found = jdbc.query(
                "SELECT group_id, inviter_id, invitee_id, status, expires_at FROM group_invitations WHERE id = ?",
                (rs, n) -> new Invitation(
                        UUID.fromString(rs.getString("group_id")),
                        UUID.fromString(rs.getString("inviter_id")),
                        UUID.fromString(rs.getString("invitee_id")),
                        rs.getString("status"),
                        rs.getTimestamp("expires_at").toInstant()),
                invitationId);
        if (found.isEmpty()) {
            throw ApiException.notFound();
        }
        return found.get(0);
    }

    private void setStatus(UUID invitationId, String status, Instant now) {
        jdbc.update("UPDATE group_invitations SET status = ?, responded_at = ? WHERE id = ?",
                status, Timestamp.from(now), invitationId);
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
}
