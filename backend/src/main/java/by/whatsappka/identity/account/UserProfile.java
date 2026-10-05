package by.whatsappka.identity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "bio", nullable = false, length = 500)
    private String bio;

    @Column(name = "status_text", nullable = false, length = 140)
    private String statusText;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(UUID userId, String displayName, Instant now) {
        this.userId = userId;
        this.displayName = displayName;
        this.bio = "";
        this.statusText = "";
        this.timezone = "UTC";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID userId() {
        return userId;
    }

    public String displayName() {
        return displayName;
    }
}
