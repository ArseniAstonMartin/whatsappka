package by.whatsappka.media;

import by.whatsappka.platform.jobs.JobQueue;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Фиксация загруженного файла: резервация снимается, медиа создаётся в UPLOADED и ставится задание обработки.
 * Всё в одной транзакции, поэтому файл не появляется без задания и без квоты.
 */
@Service
public class MediaRecorder {

    private final MediaAssetRepository assets;
    private final MediaQuota quota;
    private final JobQueue jobs;
    private final Clock clock;

    public MediaRecorder(MediaAssetRepository assets, MediaQuota quota, JobQueue jobs, Clock clock) {
        this.assets = assets;
        this.quota = quota;
        this.jobs = jobs;
        this.clock = clock;
    }

    @Transactional
    public MediaAsset record(
            UUID ownerId,
            UUID mediaId,
            MediaPurpose purpose,
            String filename,
            MediaType type,
            long size,
            UUID reservation
    ) {
        quota.release(reservation);
        MediaAsset asset = new MediaAsset(mediaId, ownerId, purpose, filename, type.mime(), size, clock.instant());
        assets.save(asset);
        jobs.enqueue("media.process", "media-" + mediaId, Map.of("mediaId", mediaId.toString()));
        return asset;
    }
}
