package by.whatsappka.identity;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserRoleGrant;
import by.whatsappka.identity.account.UserRoleId;
import by.whatsappka.identity.account.UserRoleRepository;
import by.whatsappka.identity.audit.AuditService;
import by.whatsappka.platform.web.TraceIds;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Одноразовое создание первого администратора из окружения. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BootstrapAdminService {

    /** Ключ advisory-блокировки: два экземпляра при одновременном старте не создадут двух администраторов. */
    private static final long LOCK_KEY = 7_304_211L;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,30}");
    private static final int PASSWORD_MIN_CHARS = 10;

    private final RegistrationService registration;
    private final UserRoleRepository roles;
    private final AuditService audit;
    private final EntityManager entityManager;
    private final Clock clock;

    public BootstrapAdminService(
            RegistrationService registration,
            UserRoleRepository roles,
            AuditService audit,
            EntityManager entityManager,
            Clock clock
    ) {
        this.registration = registration;
        this.roles = roles;
        this.audit = audit;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Возвращает true, если администратор создан в этом запуске. */
    @Transactional
    public boolean createIfAbsent(BootstrapAdminProperties properties) {
        if (!properties.requested()) {
            return false;
        }
        validate(properties);
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", LOCK_KEY)
                .getSingleResult();
        if (roles.countByRoleCode("ADMIN") > 0) {
            return false;
        }

        UserAccount admin = registration.registerBootstrap(properties.email(), properties.username(), properties.password());
        roles.save(new UserRoleGrant(new UserRoleId(admin.id(), "ADMIN"), clock.instant()));
        audit.record(null, "BOOTSTRAP_ADMIN_CREATED", "user", admin.id(), "Первый администратор из окружения",
                TraceIds.resolveIncoming(null));
        return true;
    }

    private static void validate(BootstrapAdminProperties properties) {
        if (properties.username() == null || !USERNAME.matcher(properties.username()).matches()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME: 3–30 латинских букв, цифр или подчёркиваний");
        }
        String password = properties.password();
        if (password == null || password.length() < PASSWORD_MIN_CHARS) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD должен быть задан и содержать не менее 10 символов");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > RegistrationService.PASSWORD_MAX_BYTES) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD длиннее 72 байт в UTF-8");
        }
    }
}
