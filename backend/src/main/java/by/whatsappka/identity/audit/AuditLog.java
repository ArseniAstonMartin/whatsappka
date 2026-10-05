package by.whatsappka.identity.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Запись журнала. Приложение только добавляет записи: UPDATE и DELETE у роли БД отозваны миграцией. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    protected AuditLog() {
    }

    public AuditLog(
            UUID id,
            UUID actorId,
            String action,
            String targetType,
            UUID targetId,
            String reason,
            Instant occurredAt,
            String traceId
    ) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.occurredAt = occurredAt;
        this.traceId = traceId;
    }
}
