package by.whatsappka.media;

import by.whatsappka.media.storage.ObjectStorage;
import by.whatsappka.media.storage.StorageException;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.settings.AppSettingsService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Потоковая загрузка: размер и назначение проверяются до чтения тела, тип — по первым байтам, квота резервируется
 * до передачи. Объект появляется в bucket раньше записи метаданных; при сбое резервация снимается, медиа не создаётся.
 */
@Service
public class MediaUploadService {

    private static final String UNKNOWN_FILENAME = "file";
    private static final int FILENAME_MAX = 255;

    private final ObjectStorage storage;
    private final MediaQuota quota;
    private final MediaRecorder recorder;
    private final AppSettingsService settings;

    public MediaUploadService(ObjectStorage storage, MediaQuota quota, MediaRecorder recorder, AppSettingsService settings) {
        this.storage = storage;
        this.quota = quota;
        this.recorder = recorder;
        this.settings = settings;
    }

    public MediaAsset upload(
            UUID ownerId,
            MediaPurpose purpose,
            String filename,
            long declaredSize,
            String declaredMime,
            InputStream body
    ) {
        if (declaredSize <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Пустой файл не принимается", List.of(), null);
        }
        if (declaredSize > settings.effectiveUploadLimit(purpose)) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large",
                    "Файл больше допустимого для этого назначения", List.of(), null);
        }

        byte[] head = readHead(body);
        MediaType type = MediaSniffer.detect(head).orElseThrow(() -> unsupported("Тип файла не поддерживается"));
        if (!purpose.allows(type)) {
            throw unsupported("Этот формат не подходит для данного назначения");
        }
        if (!claimMatches(declaredMime, type)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "mime_mismatch",
                    "Заявленный тип не совпадает с содержимым", List.of(), null);
        }

        UUID reservation = quota.reserve(ownerId, declaredSize);
        UUID mediaId = UUID.randomUUID();
        String key = MediaKeys.original(ownerId, mediaId);
        try {
            InputStream whole = new SequenceInputStream(
                    new ByteArrayInputStream(head),
                    new ValidatingStream(body, declaredSize, type.isRawText()));
            storage.put(key, whole, declaredSize, type.mime());
        } catch (MediaRejectedException rejected) {
            quota.release(reservation);
            throw new ApiException(rejected.status(), rejected.code(), rejected.getMessage(), List.of(), null);
        } catch (StorageException storageFailure) {
            quota.release(reservation);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "storage_unavailable",
                    "Хранилище файлов временно недоступно", List.of(), null);
        }
        return recorder.record(ownerId, mediaId, purpose, cleanFilename(filename), type, declaredSize, reservation);
    }

    private static byte[] readHead(InputStream body) {
        try {
            return body.readNBytes(MediaSniffer.HEAD_BYTES);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Тело запроса не удалось прочитать",
                    List.of(), null);
        }
    }

    private static boolean claimMatches(String declared, MediaType actual) {
        if (declared == null || declared.isBlank()) {
            return true;
        }
        String base = declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (base.equals("application/octet-stream")) {
            return true;
        }
        return base.equals(actual.mime());
    }

    private static ApiException unsupported(String detail) {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", detail, List.of(), null);
    }

    /** Имя нужно только для отображения: путь и управляющие символы отбрасываются. */
    static String cleanFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN_FILENAME;
        }
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return UNKNOWN_FILENAME;
        }
        return name.length() > FILENAME_MAX ? name.substring(0, FILENAME_MAX) : name;
    }
}
