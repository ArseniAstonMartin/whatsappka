package by.whatsappka.identity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Связка локального аккаунта с внешним провайдером по паре (provider, subject). */
@Entity
@Table(name = "external_identities")
public class ExternalIdentity {

    public static final String PROVIDER_GOOGLE = "GOOGLE";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExternalIdentity() {
    }

    public ExternalIdentity(UUID id, UUID userId, String provider, String subject, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.subject = subject;
        this.createdAt = createdAt;
    }

    public UUID userId() {
        return userId;
    }
}
