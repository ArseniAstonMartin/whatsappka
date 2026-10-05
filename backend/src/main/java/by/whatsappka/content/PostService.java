package by.whatsappka.content;

import by.whatsappka.communities.CommunityMembershipService;
import by.whatsappka.media.access.MediaLinkType;
import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD публикаций и немедленная публикация (TASK-041, TASK-042). Автор работает только со своими
 * записями; расписание, хештеги, лента, комментарии и реакции того же эпика — отдельные задачи.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PostService {

    static final String EVENT_PUBLISHED = "post.published";

    private final JdbcTemplate jdbc;
    private final CommunityMembershipService communities;
    private final OutboxWriter outbox;
    private final Clock clock;

    public PostService(JdbcTemplate jdbc, CommunityMembershipService communities, OutboxWriter outbox, Clock clock) {
        this.jdbc = jdbc;
        this.communities = communities;
        this.outbox = outbox;
        this.clock = clock;
    }

    public record PostSummary(
            UUID id, String body, String status, UUID groupId, long version,
            Instant updatedAt, String scheduleFailureReason
    ) {
    }

    public record PostView(
            UUID id, String body, String status, UUID groupId, List<UUID> mediaIds, List<String> hashtags,
            long version, Instant createdAt, Instant updatedAt, String scheduleFailureReason
    ) {
    }

    @Transactional
    public UUID create(UUID authorId, UUID groupId, String rawBody, List<UUID> rawMedia, List<String> rawHashtags) {
        String body = PostRules.normalizeBody(rawBody);
        List<UUID> media = rawMedia == null ? List.of() : rawMedia;
        PostRules.validateMedia(media);
        List<String> hashtags = PostRules.normalizeHashtags(rawHashtags);
        if (groupId != null && !communities.isActiveMember(groupId, authorId)) {
            throw ApiException.forbidden();
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("INSERT INTO posts (id, author_id, group_id, body, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'DRAFT', ?, ?)",
                id, authorId, groupId, body, Timestamp.from(now), Timestamp.from(now));
        attach(id, authorId, media);
        syncHashtags(id, hashtags);
        return id;
    }

    /** Правка доступна для черновика и уже опубликованной записи; published_at при этом не трогается. */
    @Transactional
    public void update(UUID postId, UUID authorId, long expectedVersion, String rawBody, List<UUID> rawMedia, List<String> rawHashtags) {
        String body = PostRules.normalizeBody(rawBody);
        List<UUID> media = rawMedia == null ? List.of() : rawMedia;
        PostRules.validateMedia(media);
        List<String> hashtags = PostRules.normalizeHashtags(rawHashtags);
        int updated = jdbc.update(
                "UPDATE posts SET body = ?, version = version + 1, updated_at = ?, schedule_failure_reason = NULL "
                        + "WHERE id = ? AND author_id = ? AND status IN ('DRAFT', 'PUBLISHED') AND deleted_at IS NULL AND version = ?",
                body, Timestamp.from(clock.instant()), postId, authorId, expectedVersion);
        if (updated == 0) {
            diagnoseUpdateFailure(postId, authorId);
        }
        jdbc.update("DELETE FROM media_links WHERE link_type = ? AND link_id = ?", MediaLinkType.POST_ATTACHMENT.name(), postId);
        jdbc.update("DELETE FROM post_media WHERE post_id = ?", postId);
        attach(postId, authorId, media);
        syncHashtags(postId, hashtags);
    }

    /**
     * Публикация: DRAFT → PUBLISHED, атомарно через CAS по статусу — конкурентный повторный вызов
     * не задевает ни одной строки и получает 409, второй публикации не возникает.
     */
    @Transactional
    public void publish(UUID postId, UUID authorId) {
        List<PublishRow> rows = jdbc.query(
                "SELECT group_id, body, status FROM posts WHERE id = ? AND author_id = ? AND deleted_at IS NULL",
                (rs, n) -> new PublishRow(
                        rs.getObject("group_id") == null ? null : UUID.fromString(rs.getString("group_id")),
                        rs.getString("body"), rs.getString("status")),
                postId, authorId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        PublishRow row = rows.get(0);
        if (!"DRAFT".equals(row.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "not_draft", "Публиковать можно только черновик", List.of(), null);
        }
        Boolean hasMedia = jdbc.queryForObject("SELECT count(*) > 0 FROM post_media WHERE post_id = ?", Boolean.class, postId);
        if ((row.body() == null || row.body().isBlank()) && !Boolean.TRUE.equals(hasMedia)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "empty_post",
                    "Нужен текст или хотя бы одно готовое изображение", List.of(), null);
        }
        if (row.groupId() != null && !communities.isActiveMember(row.groupId(), authorId)) {
            throw ApiException.forbidden();
        }
        Instant now = clock.instant();
        int updated = jdbc.update(
                "UPDATE posts SET status = 'PUBLISHED', published_at = ?, version = version + 1, updated_at = ?, "
                        + "schedule_failure_reason = NULL WHERE id = ? AND author_id = ? AND status = 'DRAFT'",
                Timestamp.from(now), Timestamp.from(now), postId, authorId);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "not_draft", "Публиковать можно только черновик", List.of(), null);
        }
        outbox.record("post", postId, EVENT_PUBLISHED, Map.of("postId", postId.toString(), "authorId", authorId.toString()));
    }

    /** Удаление не ограничено статусом: своя запись в любом состоянии пропадает из обычной выдачи. */
    @Transactional
    public void delete(UUID postId, UUID authorId) {
        int updated = jdbc.update(
                "UPDATE posts SET deleted_at = ?, version = version + 1, updated_at = ? "
                        + "WHERE id = ? AND author_id = ? AND deleted_at IS NULL",
                Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), postId, authorId);
        if (updated == 0) {
            throw ApiException.notFound();
        }
    }

    @Transactional(readOnly = true)
    public PostView get(UUID postId, UUID authorId) {
        List<Row> rows = jdbc.query(
                "SELECT id, body, status, group_id, version, created_at, updated_at, schedule_failure_reason FROM posts "
                        + "WHERE id = ? AND author_id = ? AND deleted_at IS NULL",
                ROW_MAPPER, postId, authorId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        Row row = rows.get(0);
        List<UUID> mediaIds = jdbc.query(
                "SELECT media_id FROM post_media WHERE post_id = ? ORDER BY position",
                (rs, n) -> UUID.fromString(rs.getString("media_id")), postId);
        List<String> hashtags = jdbc.query(
                "SELECT h.normalized_name FROM post_hashtags ph JOIN hashtags h ON h.id = ph.hashtag_id "
                        + "WHERE ph.post_id = ? ORDER BY h.normalized_name",
                (rs, n) -> rs.getString("normalized_name"), postId);
        return new PostView(row.id(), row.body(), row.status(), row.groupId(), mediaIds, hashtags,
                row.version(), row.createdAt(), row.updatedAt(), row.scheduleFailureReason());
    }

    /** Свои черновики и отложенные записи, от недавно изменённых. Опубликованные сюда не попадают. */
    @Transactional(readOnly = true)
    public CursorPage<PostSummary> drafts(UUID authorId, String cursor, Integer limit) {
        int size = PageSize.limit(limit);
        Keyset key = decode(cursor);
        List<Row> rows = key == null
                ? jdbc.query("SELECT id, body, status, group_id, version, created_at, updated_at, schedule_failure_reason FROM posts "
                        + "WHERE author_id = ? AND status IN ('DRAFT', 'SCHEDULED') AND deleted_at IS NULL "
                        + "ORDER BY updated_at DESC, id DESC LIMIT ?", ROW_MAPPER, authorId, size + 1)
                : jdbc.query("SELECT id, body, status, group_id, version, created_at, updated_at, schedule_failure_reason FROM posts "
                        + "WHERE author_id = ? AND status IN ('DRAFT', 'SCHEDULED') AND deleted_at IS NULL "
                        + "AND (updated_at, id) < (?, ?) ORDER BY updated_at DESC, id DESC LIMIT ?",
                        ROW_MAPPER, authorId, Timestamp.from(key.at()), key.id(), size + 1);
        boolean more = rows.size() > size;
        List<Row> shown = more ? rows.subList(0, size) : rows;
        List<PostSummary> items = shown.stream()
                .map(row -> new PostSummary(row.id(), row.body(), row.status(), row.groupId(), row.version(),
                        row.updatedAt(), row.scheduleFailureReason()))
                .toList();
        String next = more ? encode(shown.get(shown.size() - 1).updatedAt(), shown.get(shown.size() - 1).id()) : null;
        return new CursorPage<>(items, next, more);
    }

    private void attach(UUID postId, UUID authorId, List<UUID> media) {
        for (int i = 0; i < media.size(); i++) {
            UUID mediaId = media.get(i);
            requireAttachable(authorId, mediaId);
            jdbc.update("INSERT INTO post_media (post_id, media_id, position) VALUES (?, ?, ?)", postId, mediaId, i + 1);
            jdbc.update("INSERT INTO media_links (media_id, link_type, link_id, created_at) VALUES (?, ?, ?, ?)",
                    mediaId, MediaLinkType.POST_ATTACHMENT.name(), postId, Timestamp.from(clock.instant()));
        }
    }

    /** Полная замена связей поста с хештегами на переданный нормализованный набор. */
    private void syncHashtags(UUID postId, List<String> normalizedTags) {
        jdbc.update("DELETE FROM post_hashtags WHERE post_id = ?", postId);
        for (String tag : normalizedTags) {
            UUID hashtagId = upsertHashtag(tag);
            jdbc.update("INSERT INTO post_hashtags (post_id, hashtag_id) VALUES (?, ?)", postId, hashtagId);
        }
    }

    private UUID upsertHashtag(String normalized) {
        List<UUID> existing = jdbc.query("SELECT id FROM hashtags WHERE normalized_name = ?",
                (rs, n) -> UUID.fromString(rs.getString("id")), normalized);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO hashtags (id, normalized_name, display_name, created_at) VALUES (?, ?, ?, ?)",
                    id, normalized, normalized, Timestamp.from(clock.instant()));
            return id;
        } catch (DuplicateKeyException concurrentInsert) {
            return jdbc.query("SELECT id FROM hashtags WHERE normalized_name = ?",
                    (rs, n) -> UUID.fromString(rs.getString("id")), normalized).get(0);
        }
    }

    /** Вложение: готовое собственное изображение назначения POST_IMAGE, ещё не использованное в другой публикации. */
    private void requireAttachable(UUID authorId, UUID mediaId) {
        List<MediaRow> rows = jdbc.query(
                "SELECT owner_id, status, purpose, deleted_at IS NOT NULL AS deleted, "
                        + "(SELECT count(*) FROM post_media pm WHERE pm.media_id = m.id) AS attached "
                        + "FROM media_assets m WHERE m.id = ?",
                (rs, n) -> new MediaRow(
                        UUID.fromString(rs.getString("owner_id")),
                        rs.getString("status"),
                        rs.getString("purpose"),
                        rs.getBoolean("deleted"),
                        rs.getLong("attached")),
                mediaId);
        if (rows.isEmpty() || rows.get(0).deleted()) {
            throw ApiException.notFound();
        }
        MediaRow row = rows.get(0);
        if (!row.owner().equals(authorId)) {
            throw ApiException.notFound();
        }
        if (!"READY".equals(row.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "media_not_ready", "Изображение ещё не готово", List.of(), null);
        }
        if (!MediaLinkType.POST_ATTACHMENT.accepts(row.purpose())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "purpose_mismatch", "Файл не подходит для публикации", List.of(), null);
        }
        if (row.attached() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "attachment_in_use",
                    "Изображение уже используется в другой публикации", List.of(), null);
        }
    }

    /** Различает «не найдено/чужое» от «сейчас недоступно для правки» и от конфликта версии. */
    private void diagnoseUpdateFailure(UUID postId, UUID authorId) {
        List<String> status = jdbc.query(
                "SELECT status FROM posts WHERE id = ? AND author_id = ? AND deleted_at IS NULL",
                (rs, n) -> rs.getString("status"), postId, authorId);
        if (status.isEmpty()) {
            throw ApiException.notFound();
        }
        if (!List.of("DRAFT", "PUBLISHED").contains(status.get(0))) {
            throw new ApiException(HttpStatus.CONFLICT, "edit_not_allowed",
                    "Запись сейчас недоступна для редактирования", List.of(), null);
        }
        throw new ApiException(HttpStatus.CONFLICT, "version_conflict", "Запись изменена в другом месте", List.of(), null);
    }

    private record MediaRow(UUID owner, String status, String purpose, boolean deleted, long attached) {
    }

    private record PublishRow(UUID groupId, String body, String status) {
    }

    private record Row(
            UUID id, String body, String status, UUID groupId, long version,
            Instant createdAt, Instant updatedAt, String scheduleFailureReason
    ) {
    }

    private static final org.springframework.jdbc.core.RowMapper<Row> ROW_MAPPER = (rs, n) -> new Row(
            UUID.fromString(rs.getString("id")),
            rs.getString("body"),
            rs.getString("status"),
            rs.getObject("group_id") == null ? null : UUID.fromString(rs.getString("group_id")),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getString("schedule_failure_reason"));

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
            throw ApiException.badRequest("invalid_cursor", "Курсор страницы некорректен");
        }
    }
}
