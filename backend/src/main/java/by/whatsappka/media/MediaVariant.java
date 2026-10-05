package by.whatsappka.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_variants")
public class MediaVariant {

    @Id
    private UUID id;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MediaVariant() {
    }

    public MediaVariant(UUID id, UUID ownerId, UUID mediaId, VariantKind kind, long sizeBytes, Instant now) {
        this.id = id;
        this.mediaId = mediaId;
        this.kind = kind.name();
        this.objectKey = MediaKeys.variant(ownerId, mediaId, kind);
        this.sizeBytes = sizeBytes;
        this.createdAt = now;
    }

    public UUID mediaId() {
        return mediaId;
    }

    public String objectKey() {
        return objectKey;
    }
}
