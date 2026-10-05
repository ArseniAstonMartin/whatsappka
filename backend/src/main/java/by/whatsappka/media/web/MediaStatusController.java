package by.whatsappka.media.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.media.MediaAsset;
import by.whatsappka.media.MediaAssetRepository;
import by.whatsappka.media.MediaVariant;
import by.whatsappka.media.MediaVariantRepository;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.ApiV1Controller;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Статус обработки виден только владельцу. Чужой или несуществующий файл даёт одинаковый 404. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MediaStatusController {

    private final MediaAssetRepository assets;
    private final MediaVariantRepository variants;

    public MediaStatusController(MediaAssetRepository assets, MediaVariantRepository variants) {
        this.assets = assets;
        this.variants = variants;
    }

    @GetMapping("/media/{id}/status")
    public MediaStatusResponse status(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        MediaAsset asset = assets.findByIdAndOwnerId(id, user.userId())
                .filter((found) -> !found.isDeleted())
                .orElseThrow(ApiException::notFound);
        List<String> kinds = variants.findByMediaId(id).stream().map(MediaVariant::kind).sorted().toList();
        return new MediaStatusResponse(asset.id(), asset.status(), asset.failureCode(), asset.width(), asset.height(), kinds);
    }

    public record MediaStatusResponse(
            UUID id,
            String status,
            String failureCode,
            Integer width,
            Integer height,
            List<String> variants
    ) {
    }
}
