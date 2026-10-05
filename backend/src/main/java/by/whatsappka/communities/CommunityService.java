package by.whatsappka.communities;

import by.whatsappka.media.MediaPurpose;
import by.whatsappka.media.access.MediaLinkType;
import by.whatsappka.platform.cache.PublicFieldsCache;
import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Создание, настройки и удаление сообщества. Изменения состава (TASK-036) идут отдельным сервисом;
 * здесь только то, что доступно владельцу напрямую.
 *
 * Любое изменение полей, видимости, аватара, обложки или удаление обязано сбросить кэш безопасных
 * публичных полей тем же ключом ({@link CommunityPublicFields#cacheKey(UUID)}); будущие задачи
 * (модерация, передача владения), меняющие эти поля, должны делать то же самое.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommunityService {

    static final String EVENT_CREATED = "group.created";
    static final String EVENT_UPDATED = "group.updated";
    static final String EVENT_VISIBILITY_CHANGED = "group.visibility_changed";
    static final String EVENT_DELETED = "group.deleted";
    static final String EVENT_AVATAR_CHANGED = "group.avatar_changed";
    static final String EVENT_COVER_CHANGED = "group.cover_changed";

    private final JdbcTemplate jdbc;
    private final OutboxWriter outbox;
    private final PublicFieldsCache cache;
    private final Clock clock;

    public CommunityService(JdbcTemplate jdbc, OutboxWriter outbox, PublicFieldsCache cache, Clock clock) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.cache = cache;
        this.clock = clock;
    }

    @Transactional
    public UUID create(UUID ownerId, String rawSlug, String rawName, String rawDescription, String rawVisibility) {
        String slug = CommunityRules.normalizeSlug(rawSlug);
        String name = CommunityRules.normalizeName(rawName);
        String description = CommunityRules.normalizeDescription(rawDescription);
        String visibility = CommunityRules.normalizeVisibility(rawVisibility);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(CommunitySql.INSERT_GROUP, id, slug, name, description, visibility, ownerId);
        } catch (DataIntegrityViolationException duplicate) {
            throw ApiException.conflict("Такой адрес сообщества уже занят");
        }
        jdbc.update(CommunitySql.INSERT_MEMBER, id, ownerId);
        outbox.record("group", id, EVENT_CREATED, Map.of("groupId", id.toString(), "ownerId", ownerId.toString()));
        return id;
    }

    @Transactional
    public void update(UUID groupId, UUID actorId, String rawName, String rawDescription, String rawVisibility) {
        lock(groupId);
        CommunityManageRow group = requireManageRow(groupId);
        requireOwner(group, actorId);
        String name = rawName == null ? group.name() : CommunityRules.normalizeName(rawName);
        String description = rawDescription == null ? group.description() : CommunityRules.normalizeDescription(rawDescription);
        String visibility = rawVisibility == null ? group.visibility() : CommunityRules.normalizeVisibility(rawVisibility);
        jdbc.update(CommunitySql.UPDATE_FIELDS, name, description, visibility, groupId);
        cache.evict(CommunityPublicFields.cacheKey(groupId));
        boolean visibilityChanged = !visibility.equals(group.visibility());
        outbox.record("group", groupId, visibilityChanged ? EVENT_VISIBILITY_CHANGED : EVENT_UPDATED,
                Map.of("groupId", groupId.toString()));
    }

    @Transactional
    public void delete(UUID groupId, UUID actorId) {
        lock(groupId);
        CommunityManageRow group = requireManageRow(groupId);
        requireOwner(group, actorId);
        int updated = jdbc.update(CommunitySql.SOFT_DELETE, groupId);
        if (updated == 0) {
            throw ApiException.notFound();
        }
        cache.evict(CommunityPublicFields.cacheKey(groupId));
        outbox.record("group", groupId, EVENT_DELETED, Map.of("groupId", groupId.toString()));
    }

    @Transactional
    public void changeAvatar(UUID groupId, UUID actorId, UUID mediaId) {
        changeMedia(groupId, actorId, mediaId, MediaLinkType.COMMUNITY_AVATAR, MediaPurpose.AVATAR,
                CommunitySql.SET_AVATAR, EVENT_AVATAR_CHANGED);
    }

    @Transactional
    public void changeCover(UUID groupId, UUID actorId, UUID mediaId) {
        changeMedia(groupId, actorId, mediaId, MediaLinkType.COMMUNITY_COVER, MediaPurpose.COVER,
                CommunitySql.SET_COVER, EVENT_COVER_CHANGED);
    }

    private void changeMedia(
            UUID groupId, UUID actorId, UUID mediaId, MediaLinkType type, MediaPurpose purpose,
            String updateSql, String event
    ) {
        lock(groupId);
        CommunityManageRow group = requireManageRow(groupId);
        requireAdminOrOwner(group, actorId);
        Integer ready = jdbc.queryForObject(
                "SELECT count(*) FROM media_assets WHERE id = ? AND owner_id = ? AND status = 'READY' "
                        + "AND purpose = ? AND deleted_at IS NULL",
                Integer.class, mediaId, actorId, purpose.name());
        if (ready == null || ready == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "purpose_mismatch",
                    "Файл не подходит для этой привязки", List.of(), null);
        }
        jdbc.update("DELETE FROM media_links WHERE link_type = ? AND link_id = ?", type.name(), groupId);
        jdbc.update(
                "INSERT INTO media_links (media_id, link_type, link_id, created_at) VALUES (?, ?, ?, ?)",
                mediaId, type.name(), groupId, Timestamp.from(clock.instant()));
        jdbc.update(updateSql, mediaId, groupId);
        cache.evict(CommunityPublicFields.cacheKey(groupId));
        outbox.record("group", groupId, event, Map.of("groupId", groupId.toString()));
    }

    private void lock(UUID groupId) {
        jdbc.query(CommunitySql.LOCK_GROUP, rs -> { }, groupId);
    }

    private CommunityManageRow requireManageRow(UUID groupId) {
        return jdbc.query(CommunitySql.MANAGE_ROW, CommunityManageRow.MAPPER, groupId).stream()
                .findFirst()
                .filter(row -> !row.isDeleted())
                .orElseThrow(ApiException::notFound);
    }

    private static void requireOwner(CommunityManageRow group, UUID actorId) {
        if (!group.ownerId().equals(actorId)) {
            throw ApiException.forbidden();
        }
    }

    private void requireAdminOrOwner(CommunityManageRow group, UUID actorId) {
        if (group.ownerId().equals(actorId)) {
            return;
        }
        List<String> roles = jdbc.queryForList(CommunitySql.ROLE_OF, String.class, group.id(), actorId);
        if (roles.isEmpty() || !"ADMIN".equals(roles.get(0))) {
            throw ApiException.forbidden();
        }
    }
}
