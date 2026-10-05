package by.whatsappka.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
public class MediaAsset {

    public static final String STATUS_UPLOADED = "UPLOADED";

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose;

    @Column(name = "original_object_key", nullable = false, unique = true, length = 500)
    private String originalObjectKey;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "detected_mime", length = 100)
    private String detectedMime;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaAsset() {
    }

    public MediaAsset(
            UUID id,
            UUID ownerId,
            MediaPurpose purpose,
            String filename,
            String detectedMime,
            long sizeBytes,
            Instant now
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.purpose = purpose.name();
        this.originalObjectKey = MediaKeys.original(ownerId, id);
        this.filename = filename;
        this.detectedMime = detectedMime;
        this.sizeBytes = sizeBytes;
        this.status = STATUS_UPLOADED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String originalObjectKey() {
        return originalObjectKey;
    }

    public String status() {
        return status;
    }

    public String purpose() {
        return purpose;
    }

    public String filename() {
        return filename;
    }

    public String detectedMime() {
        return detectedMime;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
