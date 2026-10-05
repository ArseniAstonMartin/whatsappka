package by.whatsappka.identity.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Общая точка записи аудита: административные действия пишут сюда в той же транзакции, что и изменение. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuditService {

    private final AuditLogRepository logs;
    private final Clock clock;

    public AuditService(AuditLogRepository logs, Clock clock) {
        this.logs = logs;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, String targetType, UUID targetId, String reason, String traceId) {
        logs.save(new AuditLog(
                UUID.randomUUID(),
                actorId,
                action,
                targetType,
                targetId,
                reason,
                clock.instant(),
                traceId
        ));
    }
}
