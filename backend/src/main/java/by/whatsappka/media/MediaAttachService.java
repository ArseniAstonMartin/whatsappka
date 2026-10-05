package by.whatsappka.media;

import by.whatsappka.media.access.MediaLinkType;
import by.whatsappka.platform.web.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Привязка и отвязка. Привязать можно только собственный READY-файл нужного назначения,
 * и только к своему профилю. Удалить можно только файл без привязок.
 */
@Service
public class MediaAttachService {

    private final MediaAssetRepository assets;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MediaAttachService(MediaAssetRepository assets, JdbcTemplate jdbc, Clock clock) {
        this.assets = assets;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void attach(UUID ownerId, UUID mediaId, MediaLinkType type, UUID linkId) {
        MediaAsset asset = ownedLive(ownerId, mediaId);
        if (!MediaAsset.STATUS_READY.equals(asset.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "media_not_ready",
                    "Файл ещё не готов к привязке", List.of(), null);
        }
        if (!type.purpose().name().equals(asset.purpose())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "purpose_mismatch",
                    "Назначение файла не подходит для этой привязки", List.of(), null);
        }
        if (!linkId.equals(ownerId)) {
            // Пока доступны только привязки к собственному профилю: чужой объект изменить нельзя.
            throw ApiException.forbidden();
        }
        String column = type == MediaLinkType.PROFILE_AVATAR ? "avatar_media_id" : "cover_media_id";
        UUID previous = jdbc.query(
                "SELECT " + column + " FROM user_profiles WHERE user_id = ?",
                rs -> rs.next() ? (UUID) rs.getObject(1) : null,
                linkId);
        if (previous != null && !previous.equals(mediaId)) {
            jdbc.update("DELETE FROM media_links WHERE media_id = ? AND link_type = ? AND link_id = ?",
                    previous, type.name(), linkId);
        }
        jdbc.update(
                "INSERT INTO media_links (media_id, link_type, link_id, created_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (media_id, link_type, link_id) DO NOTHING",
                mediaId, type.name(), linkId, Timestamp.from(clock.instant()));
        jdbc.update("UPDATE user_profiles SET " + column + " = ?, updated_at = now() WHERE user_id = ?", mediaId, linkId);
    }

    @Transactional
    public void detach(UUID ownerId, UUID mediaId, MediaLinkType type, UUID linkId) {
        ownedLive(ownerId, mediaId);
        if (!linkId.equals(ownerId)) {
            throw ApiException.forbidden();
        }
        String column = type == MediaLinkType.PROFILE_AVATAR ? "avatar_media_id" : "cover_media_id";
        jdbc.update("DELETE FROM media_links WHERE media_id = ? AND link_type = ? AND link_id = ?",
                mediaId, type.name(), linkId);
        jdbc.update("UPDATE user_profiles SET " + column + " = NULL, updated_at = now() "
                + "WHERE user_id = ? AND " + column + " = ?", linkId, mediaId);
    }

    /** Мягкое удаление: только владелец и только без привязок. Доступ к байтам после этого закрыт. */
    @Transactional
    public void delete(UUID ownerId, UUID mediaId) {
        MediaAsset asset = ownedLive(ownerId, mediaId);
        Integer links = jdbc.queryForObject("SELECT count(*) FROM media_links WHERE media_id = ?", Integer.class, mediaId);
        if (links != null && links > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "media_in_use", "Файл привязан и не может быть удалён",
                    List.of(), null);
        }
        asset.markDeleted(clock.instant());
    }

    private MediaAsset ownedLive(UUID ownerId, UUID mediaId) {
        return assets.findByIdAndOwnerId(mediaId, ownerId)
                .filter((asset) -> !asset.isDeleted())
                .orElseThrow(ApiException::notFound);
    }
}
