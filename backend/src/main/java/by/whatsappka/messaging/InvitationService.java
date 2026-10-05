package by.whatsappka.messaging;

import by.whatsappka.messaging.GroupRules.Role;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Приглашения в групповой чат. Принятие атомарно: проверка прав пригласившего, блокировок и лимита
 * и добавление интервала членства выполняются в одной транзакции.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InvitationService {

    static final Duration TTL = Duration.ofDays(7);
    public static final String EVENT_CREATED = "conversation.invitation.created";
    public static final String EVENT_ACCEPTED = "conversation.invitation.accepted";

    private final JdbcTemplate jdbc;
    private final GroupService groups;
    private final SocialRelations relations;
    private final OutboxWriter outbox;
    private final Clock clock;

    public InvitationService(
            JdbcTemplate jdbc,
            GroupService groups,
            SocialRelations relations,
            OutboxWriter outbox,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.groups = groups;
        this.relations = relations;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public UUID invite(UUID viewer, UUID conversationId, UUID inviteeId) {
        if (viewer.equals(inviteeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "self_invite", "Нельзя пригласить себя", List.of(), null);
        }
        groups.lock(conversationId);
        Role actor = groups.activeRole(conversationId, viewer);
        if (actor == null) {
            throw ApiException.notFound();
        }
        if (!InvitationRules.canInvite(actor)) {
            throw ApiException.forbidden();
        }
        relations.requireVisibleTo(viewer, inviteeId);
        if (groups.activeRole(conversationId, inviteeId) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "already_member", "Пользователь уже в чате", List.of(), null);
        }
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO conversation_invitations (id, conversation_id, inviter_id, invitee_id, status, expires_at, created_at) "
                    + "VALUES (?, ?, ?, ?, 'PENDING', ?, ?)",
                    id, conversationId, viewer, inviteeId, Timestamp.from(now.plus(TTL)), Timestamp.from(now));
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "invitation_pending", "Приглашение уже ожидает ответа", List.of(), null);
        }
        outbox.record("conversation", conversationId, EVENT_CREATED, Map.of(
                "invitationId", id.toString(),
                "conversationId", conversationId.toString(),
                "inviteeId", inviteeId.toString()));
        return id;
    }

    /**
     * Принятие. Истёкшее приглашение помечается EXPIRED и остаётся в таком виде даже при ответе с ошибкой,
     * поэтому откат для ApiException отключён.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public UUID accept(UUID viewer, UUID invitationId) {
        Invitation invitation = load(invitationId);
        groups.lock(invitation.conversationId());
        Instant now = clock.instant();
        if (!viewer.equals(invitation.inviteeId())) {
            throw ApiException.notFound();
        }
        if (!"PENDING".equals(invitation.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "invitation_closed", "Приглашение уже закрыто", List.of(), null);
        }
        if (!invitation.expiresAt().isAfter(now)) {
            setStatus(invitationId, "EXPIRED", now);
            throw new ApiException(HttpStatus.CONFLICT, "invitation_expired", "Срок приглашения истёк", List.of(), null);
        }
        Role inviterRole = groups.activeRole(invitation.conversationId(), invitation.inviterId());
        if (inviterRole == null || !InvitationRules.inviterStillEntitled(inviterRole)) {
            setStatus(invitationId, "REVOKED", now);
            throw new ApiException(HttpStatus.CONFLICT, "inviter_no_longer_allowed",
                    "Пригласивший больше не может приглашать в этот чат", List.of(), null);
        }
        relations.requireVisibleTo(viewer, invitation.inviterId());
        // Лимит проверяем здесь, а не внутри join: ошибка транзакционного метода пометила бы общую транзакцию
        // к откату, и ответ 409 превратился бы в неожиданный откат.
        if (!GroupRules.hasRoomFor(groups.activeCount(invitation.conversationId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "chat_full", "В чате уже максимальное число участников",
                    List.of(), null);
        }
        groups.join(invitation.conversationId(), viewer);
        setStatus(invitationId, "ACCEPTED", now);
        outbox.record("conversation", invitation.conversationId(), EVENT_ACCEPTED, Map.of(
                "invitationId", invitationId.toString(),
                "inviteeId", viewer.toString()));
        return invitation.conversationId();
    }

    @Transactional
    public void decline(UUID viewer, UUID invitationId) {
        Invitation invitation = load(invitationId);
        if (!viewer.equals(invitation.inviteeId())) {
            throw ApiException.notFound();
        }
        if (!"PENDING".equals(invitation.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "invitation_closed", "Приглашение уже закрыто", List.of(), null);
        }
        setStatus(invitationId, "DECLINED", clock.instant());
    }

    @Transactional
    public void revoke(UUID viewer, UUID invitationId) {
        Invitation invitation = load(invitationId);
        groups.lock(invitation.conversationId());
        Role viewerRole = groups.activeRole(invitation.conversationId(), viewer);
        if (!InvitationRules.canRevoke(viewer, invitation.inviterId(), viewerRole)) {
            throw ApiException.forbidden();
        }
        if (!"PENDING".equals(invitation.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "invitation_closed", "Приглашение уже закрыто", List.of(), null);
        }
        setStatus(invitationId, "REVOKED", clock.instant());
    }

    /** Входящие приглашения получателя, которые ещё можно принять. */
    @Transactional(readOnly = true)
    public List<PendingView> pendingFor(UUID viewer) {
        return jdbc.query(
                "SELECT i.id, i.conversation_id, c.title, i.expires_at FROM conversation_invitations i "
                        + "JOIN conversations c ON c.id = i.conversation_id "
                        + "WHERE i.invitee_id = ? AND i.status = 'PENDING' AND i.expires_at > ? ORDER BY i.created_at DESC, i.id",
                (rs, n) -> new PendingView(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("conversation_id")),
                        rs.getString("title"),
                        rs.getTimestamp("expires_at").toInstant()),
                viewer, Timestamp.from(clock.instant()));
    }

    public record PendingView(UUID id, UUID conversationId, String conversationTitle, Instant expiresAt) {
    }

    private record Invitation(UUID conversationId, UUID inviterId, UUID inviteeId, String status, Instant expiresAt) {
    }

    private Invitation load(UUID invitationId) {
        List<Invitation> found = jdbc.query(
                "SELECT conversation_id, inviter_id, invitee_id, status, expires_at FROM conversation_invitations WHERE id = ?",
                (rs, n) -> new Invitation(
                        UUID.fromString(rs.getString("conversation_id")),
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
        jdbc.update("UPDATE conversation_invitations SET status = ?, responded_at = ? WHERE id = ?",
                status, Timestamp.from(now), invitationId);
    }
}
