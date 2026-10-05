package by.whatsappka.media.processing;

import by.whatsappka.media.MediaAsset;
import by.whatsappka.media.MediaAssetRepository;
import by.whatsappka.media.MediaKeys;
import by.whatsappka.media.MediaType;
import by.whatsappka.media.MediaVariant;
import by.whatsappka.media.MediaVariantRepository;
import by.whatsappka.media.VariantKind;
import by.whatsappka.media.processing.ImageProcessor.ImageRejectedException;
import by.whatsappka.media.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Обработка медиа в worker. Переходы UPLOADED → PROCESSING → READY/FAILED. Постоянные отказы по содержимому
 * переводят файл в FAILED с причиной. Временные сбои хранилища пробрасываются: задание повторится.
 * Ключи вариантов детерминированы, поэтому повтор не создаёт новых объектов, а лишь дописывает недостающие.
 */
@Service
public class MediaProcessor {

    private static final Set<MediaType> IMAGES = Set.of(MediaType.JPEG, MediaType.PNG, MediaType.WEBP);

    private final MediaAssetRepository assets;
    private final MediaVariantRepository variants;
    private final ObjectStorage storage;
    private final TransactionTemplate tx;
    private final Clock clock;

    public MediaProcessor(
            MediaAssetRepository assets,
            MediaVariantRepository variants,
            ObjectStorage storage,
            PlatformTransactionManager transactions,
            Clock clock
    ) {
        this.assets = assets;
        this.variants = variants;
        this.storage = storage;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    public void process(UUID mediaId) throws IOException {
        MediaAsset asset = tx.execute(status -> {
            MediaAsset found = assets.findById(mediaId).orElse(null);
            if (found == null || found.isDeleted() || !(found.status().equals(MediaAsset.STATUS_UPLOADED)
                    || found.status().equals(MediaAsset.STATUS_PROCESSING))) {
                return null;
            }
            found.startProcessing(clock.instant());
            return found;
        });
        if (asset == null) {
            return;
        }
        MediaType type = typeOf(asset);
        if (!IMAGES.contains(type)) {
            // PDF и TXT не рендерятся на сервере: достаточно подтвердить, что файл принят.
            markReady(mediaId, null, null);
            return;
        }
        try {
            byte[] original = readOriginal(asset);
            ImageProcessor.Result result = ImageProcessor.process(original, type);
            for (ImageProcessor.Variant variant : result.variants()) {
                writeVariant(asset, variant);
            }
            markReady(mediaId, result, asset.ownerId());
        } catch (ImageRejectedException rejected) {
            markFailed(mediaId, rejected.code());
        }
    }

    private byte[] readOriginal(MediaAsset asset) throws IOException {
        try (InputStream in = storage.get(asset.originalObjectKey())) {
            return in.readAllBytes();
        }
    }

    /** Вариант уже записан при прошлом запуске: повтор его не перезаписывает и не дублирует. */
    private void writeVariant(MediaAsset asset, ImageProcessor.Variant variant) {
        String key = MediaKeys.variant(asset.ownerId(), asset.id(), variant.kind());
        if (storage.head(key).isEmpty()) {
            storage.put(key, new ByteArrayInputStream(variant.content()), variant.content().length, variant.mime());
        }
    }

    private void markReady(UUID mediaId, ImageProcessor.Result result, UUID ownerId) {
        tx.executeWithoutResult(status -> {
            MediaAsset asset = assets.findById(mediaId).orElseThrow();
            Instant now = clock.instant();
            if (result != null) {
                for (ImageProcessor.Variant variant : result.variants()) {
                    if (variants.findByMediaId(mediaId).stream().noneMatch((v) -> v.kind().equals(variant.kind().name()))) {
                        variants.save(new MediaVariant(UUID.randomUUID(), ownerId, mediaId, variant.kind(),
                                variant.content().length, variant.width(), variant.height(), now));
                    }
                }
            }
            asset.markReady(result == null ? null : result.width(), result == null ? null : result.height(), now);
        });
    }

    private void markFailed(UUID mediaId, String code) {
        tx.executeWithoutResult(status -> assets.findById(mediaId)
                .ifPresent((asset) -> asset.markFailed(code, clock.instant())));
    }

    private static MediaType typeOf(MediaAsset asset) {
        for (MediaType type : MediaType.values()) {
            if (type.mime().equals(asset.detectedMime())) {
                return type;
            }
        }
        throw new IllegalStateException("Неизвестный тип медиа у записи");
    }
}
