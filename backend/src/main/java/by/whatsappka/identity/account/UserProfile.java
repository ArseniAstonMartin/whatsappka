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

    @Column(name = "avatar_media_id")
    private UUID avatarMediaId;

    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    /** Заполняется только административным действием; пользователь флаг не меняет. */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

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

    public String bio() {
        return bio;
    }

    public String statusText() {
        return statusText;
    }

    public String timezone() {
        return timezone;
    }

    public UUID avatarMediaId() {
        return avatarMediaId;
    }

    public UUID coverMediaId() {
        return coverMediaId;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    /** Меняет только переданные поля (null — без изменений). Проверки длины и формата делает слой DTO. */
    public void update(String newDisplayName, String newBio, String newStatusText, String newTimezone, Instant now) {
        if (newDisplayName != null) {
            this.displayName = newDisplayName;
        }
        if (newBio != null) {
            this.bio = newBio;
        }
        if (newStatusText != null) {
            this.statusText = newStatusText;
        }
        if (newTimezone != null) {
            this.timezone = newTimezone;
        }
        this.updatedAt = now;
    }
}
