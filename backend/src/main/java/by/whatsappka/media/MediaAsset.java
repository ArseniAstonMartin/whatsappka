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
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

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

    public Integer width() {
        return width;
    }

    public Integer height() {
        return height;
    }

    public String failureCode() {
        return failureCode;
    }

    /** UPLOADED или повтор из PROCESSING. Готовое и неудачное медиа не обрабатывается снова. */
    public void startProcessing(Instant now) {
        if (!STATUS_UPLOADED.equals(status) && !STATUS_PROCESSING.equals(status)) {
            throw new IllegalStateException("Обработка возможна только для UPLOADED или PROCESSING");
        }
        this.status = STATUS_PROCESSING;
        this.updatedAt = now;
    }

    public void markReady(Integer width, Integer height, Instant now) {
        this.status = STATUS_READY;
        this.width = width;
        this.height = height;
        this.failureCode = null;
        this.updatedAt = now;
    }

    public void markFailed(String code, Instant now) {
        this.status = STATUS_FAILED;
        this.failureCode = code;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
