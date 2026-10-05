package by.whatsappka.sanctions;

import by.whatsappka.identity.SessionService;
import by.whatsappka.identity.account.UserRoleRepository;
import by.whatsappka.identity.audit.AuditService;
import by.whatsappka.moderation.ModerationRules;
import by.whatsappka.notifications.NotificationService;
import by.whatsappka.notifications.NotificationType;
import by.whatsappka.platform.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Выдача и снятие санкций. Выдача одной транзакцией: запись санкции, перевод аккаунта в SUSPENDED, отзыв сессий,
 * аудит и системное уведомление. Истечение срока обрабатывает SanctionExpirySweeper.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SanctionService {

    public record Issued(UUID id, String kind, Instant expiresAt) {
    }

    private final JdbcTemplate jdbc;
    private final UserRoleRepository roles;
    private final SessionService sessions;
    private final AuditService audit;
    private final NotificationService notifications;

    public SanctionService(JdbcTemplate jdbc, UserRoleRepository roles, SessionService sessions,
                           AuditService audit, NotificationService notifications) {
        this.jdbc = jdbc;
        this.roles = roles;
        this.sessions = sessions;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional
    public Issued issue(UUID actor, boolean actorIsAdmin, UUID targetId, String rawKind, Integer durationHours,
                        String rawReason, String traceId) {
        SanctionRules.Kind kind = SanctionRules.parseKind(rawKind);
        SanctionRules.requireDuration(kind, durationHours);
        String reason = ModerationRules.requireReason(rawReason);
        if (actor.equals(targetId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "self_sanction", "Нельзя ограничить собственный аккаунт", List.of(), null);
        }
        List<String> status = jdbc.queryForList(SanctionSql.LOCK_USER, String.class, targetId);
        if (status.isEmpty()) {
            throw ApiException.notFound();
        }
        if ("DISABLED".equals(status.get(0))) {
            throw new ApiException(HttpStatus.CONFLICT, "account_disabled", "Аккаунт уже отключён", List.of(), null);
        }
        SanctionRules.requireMayIssue(actorIsAdmin, kind, roles.findRoleCodes(targetId));

        UUID id = UUID.randomUUID();
        Integer hours = kind == SanctionRules.Kind.TEMPORARY ? durationHours : null;
        Instant expiresAt = jdbc.queryForObject(SanctionSql.INSERT, (rs, n) ->
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                id, targetId, kind.name(), reason, actor, hours, hours);
        jdbc.update(SanctionSql.SUSPEND, targetId);
        sessions.revokeAll(targetId);
        audit.record(actor, "SANCTION_ISSUED", "user", targetId, reason, traceId);
        notifications.notify(targetId, "sanction:" + id + ":issued", NotificationType.SYSTEM, null, null, null, null);
        return new Issued(id, kind.name(), expiresAt);
    }

    @Transactional
    public void lift(UUID admin, UUID sanctionId, String rawReason, String traceId) {
        String reason = ModerationRules.requireReason(rawReason);
        List<UUID> owner = jdbc.queryForList(SanctionSql.OWNER_OF_SANCTION, UUID.class, sanctionId);
        if (owner.isEmpty()) {
            throw ApiException.notFound();
        }
        jdbc.queryForList(SanctionSql.LOCK_USER, String.class, owner.get(0));
        if (jdbc.update(SanctionSql.LIFT, admin, reason, sanctionId) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "not_active", "Санкция уже снята или истекла", List.of(), null);
        }
        if (!hasActiveSanction(owner.get(0))) {
            jdbc.update(SanctionSql.RESTORE_ACCOUNT, owner.get(0));
        }
        audit.record(admin, "SANCTION_LIFTED", "user", owner.get(0), reason, traceId);
        notifications.notify(owner.get(0), "sanction:" + sanctionId + ":lifted", NotificationType.SYSTEM, null, null, null, null);
    }

    static boolean hasActiveSanction(JdbcTemplate jdbc, UUID userId) {
        Boolean active = jdbc.queryForObject(SanctionSql.HAS_ACTIVE_SANCTION, Boolean.class, userId);
        return Boolean.TRUE.equals(active);
    }

    private boolean hasActiveSanction(UUID userId) {
        return hasActiveSanction(jdbc, userId);
    }
}
