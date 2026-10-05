package by.whatsappka.identity.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "device_label", nullable = false, length = 150)
    private String deviceLabel;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuthSession() {
    }

    public AuthSession(UUID id, UUID userId, Instant absoluteExpiresAt, String deviceLabel, Instant now) {
        this.id = id;
        this.userId = userId;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.deviceLabel = deviceLabel;
        this.lastUsedAt = now;
        this.createdAt = now;
    }

    public UUID id() {
        return id;
    }

    public Instant absoluteExpiresAt() {
        return absoluteExpiresAt;
    }
}
