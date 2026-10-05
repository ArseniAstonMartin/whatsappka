package by.whatsappka.admin.users;

import by.whatsappka.admin.AdminRoleRules;
import by.whatsappka.identity.audit.AuditService;
import by.whatsappka.identity.account.UserRoleRepository;
import by.whatsappka.moderation.ModerationRules;
import by.whatsappka.platform.realtime.StompConnections;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.profiles.ProfileService;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Роли и верификация. Все изменения одной транзакцией с аудитом. Побочные эффекты — закрытие WebSocket
 * и сброс кэша публичного профиля — выполняются только после фиксации.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminUserService {

    public record RolesResult(UUID userId, List<String> roles) {
    }

    public record VerifiedResult(UUID userId, boolean verified) {
    }

    private final JdbcTemplate jdbc;
    private final UserRoleRepository roles;
    private final AuditService audit;
    private final StompConnections realtime;
    private final ProfileService profiles;

    public AdminUserService(JdbcTemplate jdbc, UserRoleRepository roles, AuditService audit,
                            StompConnections realtime, ProfileService profiles) {
        this.jdbc = jdbc;
        this.roles = roles;
        this.audit = audit;
        this.realtime = realtime;
        this.profiles = profiles;
    }

    @Transactional
    public RolesResult setRoles(UUID admin, UUID target, List<String> requested, String rawReason, String traceId) {
        Set<String> wanted = AdminRoleRules.parseRoles(requested);
        String reason = ModerationRules.requireReason(rawReason);
        lockTarget(target);
        Set<String> current = new TreeSet<>(jdbc.queryForList(AdminUserSql.CURRENT_ROLES, String.class, target));
        current.retainAll(AdminRoleRules.MANAGED);

        if (current.contains("ADMIN") && !wanted.contains("ADMIN")) {
            // Блокировка строк ролей ADMIN до подсчёта: параллельные снятия выстраиваются в очередь.
            jdbc.queryForList(AdminUserSql.LOCK_ADMIN_ROLES, UUID.class);
            Long active = jdbc.queryForObject(AdminUserSql.COUNT_ACTIVE_ADMINS, Long.class);
            if (active == null || active <= 1) {
                throw new ApiException(HttpStatus.CONFLICT, "last_admin",
                        "Нельзя снять роль с последнего активного администратора", List.of(), null);
            }
        }

        boolean changed = false;
        for (String role : wanted) {
            if (!current.contains(role)) {
                jdbc.update(AdminUserSql.GRANT, target, role);
                audit.record(admin, "ROLE_GRANTED", "user", target, reason + " [" + role + "]", traceId);
                changed = true;
            }
        }
        for (String role : current) {
            if (!wanted.contains(role)) {
                jdbc.update(AdminUserSql.REVOKE, target, role);
                audit.record(admin, "ROLE_REVOKED", "user", target, reason + " [" + role + "]", traceId);
                changed = true;
            }
        }
        if (changed) {
            afterCommit(() -> realtime.closeUser(target));
        }
        return new RolesResult(target, List.copyOf(new TreeSet<>(roles.findRoleCodes(target))));
    }

    @Transactional
    public VerifiedResult setVerified(UUID admin, UUID target, boolean verified, String rawReason, String traceId) {
        String reason = ModerationRules.requireReason(rawReason);
        lockTarget(target);
        int changed = verified
                ? jdbc.update(AdminUserSql.VERIFY, admin, target)
                : jdbc.update(AdminUserSql.UNVERIFY, target);
        if (changed == 1) {
            audit.record(admin, verified ? "USER_VERIFIED" : "USER_UNVERIFIED", "user", target, reason, traceId);
            afterCommit(() -> profiles.evictPublicFields(target));
        }
        return new VerifiedResult(target, verified);
    }

    private void lockTarget(UUID target) {
        if (jdbc.queryForList(AdminUserSql.LOCK_USER, UUID.class, target).isEmpty()) {
            throw ApiException.notFound();
        }
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
