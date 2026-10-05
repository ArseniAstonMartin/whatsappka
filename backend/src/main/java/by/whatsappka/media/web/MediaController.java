package by.whatsappka.media.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.media.MediaAsset;
import by.whatsappka.media.MediaPurpose;
import by.whatsappka.media.MediaUploadService;
import by.whatsappka.platform.ratelimit.RateLimiter;
import by.whatsappka.platform.ratelimit.RateLimitPolicy;
import by.whatsappka.platform.ratelimit.ConcurrencyLimiter;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.ApiV1Controller;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Загрузка файла телом запроса. Content-Length обязателен: размер и квота проверяются до чтения файла. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MediaController {

    private static final String FILENAME_HEADER = "X-Filename";

    private final MediaUploadService uploads;

    private final RateLimiter limits;
    private final ConcurrencyLimiter concurrency;

    public MediaController(MediaUploadService uploads, RateLimiter limits, ConcurrencyLimiter concurrency) {
        this.limits = limits;
        this.concurrency = concurrency;
        this.uploads = uploads;
    }

    @PostMapping("/media")
    public ResponseEntity<MediaResponse> upload(
            @RequestParam("purpose") String purpose,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request
    ) throws IOException {
        MediaPurpose parsed = MediaPurpose.parse(purpose).orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST, "invalid_purpose", "Неизвестное назначение файла", List.of(), null));
        long length = request.getContentLengthLong();
        if (length < 0) {
            throw new ApiException(HttpStatus.LENGTH_REQUIRED, "length_required",
                    "Укажите размер файла в Content-Length", List.of(), null);
        }
        // PRD: 10 загрузок в минуту и не более двух одновременных на пользователя.
        limits.consume("upload", user.userId().toString(), 10, RateLimitPolicy.MINUTE);
        MediaAsset asset;
        try (ConcurrencyLimiter.Lease ignored = concurrency.acquire(user.userId().toString(), 2)) {
            asset = uploads.upload(
                    user.userId(),
                    parsed,
                    decodeFilename(request.getHeader(FILENAME_HEADER)),
                    length,
                    request.getHeader(HttpHeaders.CONTENT_TYPE),
                    request.getInputStream()
            );
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(MediaResponse.of(asset));
    }

    /** Клиент присылает имя в URL-кодировке, чтобы кириллица и пробелы не ломали заголовок. */
    static String decodeFilename(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return raw;
        }
    }

    public record MediaResponse(UUID id, String status, String purpose, String mime, long sizeBytes, String filename) {

        static MediaResponse of(MediaAsset asset) {
            return new MediaResponse(
                    asset.id(), asset.status(), asset.purpose(), asset.detectedMime(), asset.sizeBytes(), asset.filename());
        }
    }
}
