package by.whatsappka.communities;

import by.whatsappka.platform.cache.PublicFieldsCache;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Каталог, карточка и список своих сообществ. Авторизация (видимость, членство, удаление) всегда
 * проверяется заново на PostgreSQL; кэшу (TASK-034) доверены только description/avatar/cover.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommunityQueries {

    private final JdbcTemplate jdbc;
    private final PublicFieldsCache cache;

    public CommunityQueries(JdbcTemplate jdbc, PublicFieldsCache cache) {
        this.jdbc = jdbc;
        this.cache = cache;
    }

    public record CommunitySummary(UUID id, String slug, String name, String visibility, UUID avatarMediaId) {
    }

    /**
     * Карточка сообщества. Если группа приватна и зритель не состоит в ней, поля description/ownerId/
     * coverMediaId/memberCount/viewerRole пустые, а restricted = true: это разрешённые метаданные,
     * а не полная карточка.
     */
    public record CommunityView(
            UUID id,
            String slug,
            String name,
            String description,
            String visibility,
            UUID ownerId,
            UUID avatarMediaId,
            UUID coverMediaId,
            long memberCount,
            String viewerRole,
            boolean restricted
    ) {
    }

    @Transactional(readOnly = true)
    public CommunityView detailById(UUID viewerId, UUID groupId) {
        CommunityRow row = jdbc.query(CommunitySql.ROW_BY_ID, CommunityRow.MAPPER, groupId).stream()
                .findFirst().orElseThrow(ApiException::notFound);
        return detail(viewerId, row);
    }

    @Transactional(readOnly = true)
    public CommunityView detailBySlug(UUID viewerId, String slug) {
        CommunityRow row = jdbc.query(CommunitySql.ROW_BY_SLUG, CommunityRow.MAPPER, slug).stream()
                .findFirst().orElseThrow(ApiException::notFound);
        return detail(viewerId, row);
    }

    @Transactional(readOnly = true)
    public CursorPage<CommunitySummary> catalog(String cursor, Integer limit) {
        int size = PageSize.limit(limit);
        Keyset key = decode(cursor);
        List<CatalogRow> rows = key == null
                ? jdbc.query(CommunitySql.CATALOG_FIRST, CATALOG_ROW, size + 1)
                : jdbc.query(CommunitySql.CATALOG_AFTER, CATALOG_ROW, Timestamp.from(key.at()), key.id(), size + 1);
        boolean more = rows.size() > size;
        List<CatalogRow> shown = more ? rows.subList(0, size) : rows;
        List<CommunitySummary> items = shown.stream()
                .map(row -> new CommunitySummary(row.id(), row.slug(), row.name(), "PUBLIC", row.avatarMediaId()))
                .toList();
        String next = more ? encode(shown.get(shown.size() - 1).createdAt(), shown.get(shown.size() - 1).id()) : null;
        return new CursorPage<>(items, next, more);
    }

    /** Свои сообщества (владелец или участник), включая приватные. Не постранично: число групп у человека мало. */
    @Transactional(readOnly = true)
    public List<CommunitySummary> myGroups(UUID viewerId) {
        return jdbc.query(CommunitySql.MY_GROUPS, (rs, n) -> new CommunitySummary(
                UUID.fromString(rs.getString("id")),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("visibility"),
                rs.getObject("avatar_media_id") == null ? null : UUID.fromString(rs.getString("avatar_media_id"))
        ), viewerId);
    }

    private CommunityView detail(UUID viewerId, CommunityRow group) {
        if (group.isDeleted()) {
            throw ApiException.notFound();
        }
        String role = roleOf(group, viewerId);
        boolean member = role != null;
        if (group.isHidden() && !member) {
            throw ApiException.notFound();
        }
        if (!group.isPublic() && !member) {
            return new CommunityView(group.id(), group.slug(), group.name(), null, group.visibility(),
                    null, null, null, 0L, null, true);
        }
        CommunityPublicFields fields = cachedPublicFields(group.id());
        Long memberCount = jdbc.queryForObject(CommunitySql.MEMBER_COUNT, Long.class, group.id());
        return new CommunityView(group.id(), group.slug(), group.name(), fields.description(), group.visibility(),
                group.ownerId(), fields.avatarMediaId(), fields.coverMediaId(),
                memberCount == null ? 0L : memberCount, role, false);
    }

    private String roleOf(CommunityRow group, UUID userId) {
        if (group.ownerId().equals(userId)) {
            return CommunityRules.Role.OWNER.name();
        }
        List<String> roles = jdbc.queryForList(CommunitySql.ROLE_OF, String.class, group.id(), userId);
        return roles.isEmpty() ? null : roles.get(0);
    }

    private CommunityPublicFields cachedPublicFields(UUID groupId) {
        String key = CommunityPublicFields.cacheKey(groupId);
        return cache.get(key, CommunityPublicFields.class).orElseGet(() -> {
            CommunityPublicFields fresh = jdbc.query(CommunitySql.PUBLIC_FIELDS, (rs, n) -> new CommunityPublicFields(
                    rs.getString("description"),
                    rs.getObject("avatar_media_id") == null ? null : UUID.fromString(rs.getString("avatar_media_id")),
                    rs.getObject("cover_media_id") == null ? null : UUID.fromString(rs.getString("cover_media_id"))
            ), groupId).stream().findFirst().orElseThrow(ApiException::notFound);
            cache.put(key, fresh);
            return fresh;
        });
    }

    private record CatalogRow(UUID id, String slug, String name, UUID avatarMediaId, Instant createdAt) {
    }

    private static final RowMapper<CatalogRow> CATALOG_ROW = (rs, n) -> new CatalogRow(
            UUID.fromString(rs.getString("id")),
            rs.getString("slug"),
            rs.getString("name"),
            rs.getObject("avatar_media_id") == null ? null : UUID.fromString(rs.getString("avatar_media_id")),
            rs.getTimestamp("created_at").toInstant());

    private record Keyset(Instant at, UUID id) {
    }

    private static String encode(Instant at, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((at.toString() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static Keyset decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            return new Keyset(Instant.parse(raw.substring(0, separator)), UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException malformed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_cursor", "Курсор страницы некорректен",
                    List.of(), null);
        }
    }
}
