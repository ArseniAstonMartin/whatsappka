package by.whatsappka.media;

import by.whatsappka.media.access.MediaAccessPolicy;
import by.whatsappka.media.storage.ObjectMissingException;
import by.whatsappka.media.storage.ObjectStorage;
import by.whatsappka.media.storage.StorageException;
import by.whatsappka.platform.web.ApiException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Открытие байтов файла. Проверка доступа и состояния идёт до открытия потока, поэтому при отказе байты не уходят.
 * Для всех отказов (нет файла, нет доступа, удалён, не готов) возвращается одинаковый 404.
 */
@Service
public class MediaContentService {

    public record Content(InputStream stream, String mime, long size, String filename, boolean attachment) {
    }

    private final MediaAssetRepository assets;
    private final MediaVariantRepository variants;
    private final MediaAccessPolicy policy;
    private final ObjectStorage storage;

    public MediaContentService(
            MediaAssetRepository assets,
            MediaVariantRepository variants,
            MediaAccessPolicy policy,
            ObjectStorage storage
    ) {
        this.assets = assets;
        this.variants = variants;
        this.policy = policy;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public Content open(UUID viewerId, UUID mediaId, VariantKind variant) {
        MediaAsset asset = assets.findById(mediaId)
                .filter((found) -> !found.isDeleted())
                .orElseThrow(ApiException::notFound);
        if (!policy.canView(viewerId, mediaId, asset.ownerId())) {
            throw ApiException.notFound();
        }
        if (MediaAsset.STATUS_FAILED.equals(asset.status())) {
            throw ApiException.notFound();
        }
        boolean attachment = asset.detectedMime().equals(MediaType.PDF.mime())
                || asset.detectedMime().equals(MediaType.TXT.mime());
        String key;
        String mime;
        if (variant == null) {
            key = asset.originalObjectKey();
            mime = asset.detectedMime();
        } else {
            if (!MediaAsset.STATUS_READY.equals(asset.status())) {
                throw ApiException.notFound();
            }
            MediaVariant found = variants.findByMediaId(mediaId).stream()
                    .filter((v) -> v.kind().equals(variant.name()))
                    .findFirst()
                    .orElseThrow(ApiException::notFound);
            key = found.objectKey();
            mime = asset.detectedMime().equals(MediaType.JPEG.mime()) ? MediaType.JPEG.mime() : MediaType.PNG.mime();
        }
        try {
            InputStream stream = storage.get(key);
            long size = storage.head(key).map(ObjectStorage.ObjectInfo::size).orElse(-1L);
            return new Content(stream, mime, size, asset.filename(), attachment);
        } catch (ObjectMissingException missing) {
            throw ApiException.notFound();
        } catch (StorageException failure) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "storage_unavailable",
                    "Хранилище файлов временно недоступно", java.util.List.of(), null);
        }
    }
}
