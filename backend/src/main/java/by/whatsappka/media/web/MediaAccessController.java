package by.whatsappka.media.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.media.MediaAttachService;
import by.whatsappka.media.MediaContentService;
import by.whatsappka.media.VariantKind;
import by.whatsappka.media.access.MediaLinkType;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.ApiV1Controller;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import java.nio.charset.StandardCharsets;

/** Выдача байтов через backend, привязка, отвязка и удаление. Прямых URL к bucket не существует. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MediaAccessController {

    private final MediaContentService content;
    private final MediaAttachService attach;

    public MediaAccessController(MediaContentService content, MediaAttachService attach) {
        this.content = content;
        this.attach = attach;
    }

    @GetMapping("/media/{id}/content")
    public ResponseEntity<InputStreamResource> original(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return respond(viewer, id, null);
    }

    @GetMapping("/media/{id}/variants/{size}")
    public ResponseEntity<InputStreamResource> variant(
            @PathVariable("id") UUID id,
            @PathVariable("size") String size,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return respond(viewer, id, parseVariant(size));
    }

    @PostMapping("/media/{id}/attach")
    public ResponseEntity<Void> attach(
            @PathVariable("id") UUID id,
            @RequestBody AttachRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        attach.attach(user.userId(), id, linkType(request.linkType()), request.linkId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/media/{id}/detach")
    public ResponseEntity<Void> detach(
            @PathVariable("id") UUID id,
            @RequestBody AttachRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        attach.detach(user.userId(), id, linkType(request.linkType()), request.linkId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/media/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        attach.delete(user.userId(), id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<InputStreamResource> respond(AuthenticatedUser viewer, UUID id, VariantKind variant) {
        MediaContentService.Content found = content.open(viewer.userId(), id, variant);
        ContentDisposition disposition = found.attachment()
                ? ContentDisposition.attachment().filename(found.filename(), StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(found.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(found.mime()))
                .contentLength(found.size() >= 0 ? found.size() : -1)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new InputStreamResource(found.stream()));
    }

    private static VariantKind parseVariant(String size) {
        return switch (size) {
            case "320" -> VariantKind.SIZE_320;
            case "1280" -> VariantKind.SIZE_1280;
            default -> throw ApiException.notFound();
        };
    }

    /** Неизвестный тип связи отклоняется с ошибкой, а не принимается молча. */
    private static MediaLinkType linkType(String raw) {
        try {
            return MediaLinkType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unknown_link_type", "Неизвестный тип связи",
                    List.of(), null);
        }
    }

    public record AttachRequest(String linkType, UUID linkId) {
    }
}
