package by.whatsappka.content;

import by.whatsappka.communities.CommunityMembershipService;
import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import by.whatsappka.social.SocialRelations;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Вложенные комментарии (TASK-048). Родитель всегда принадлежит тому же посту (составной FK),
 * глубина не превышает 3: ответ на комментарий третьего уровня присоединяется к той же тройке,
 * адресат фиксируется отдельно — так видимая структура не растёт глубже, но адресат не теряется.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommentService {

    static final String EVENT_CREATED = "comment.created";

    private final JdbcTemplate jdbc;
    private final CommunityMembershipService communities;
    private final SocialRelations relations;
    private final OutboxWriter outbox;
    private final ReactionService reactions;
    private final Clock clock;

    public CommentService(
            JdbcTemplate jdbc, CommunityMembershipService communities, SocialRelations relations,
            OutboxWriter outbox, ReactionService reactions, Clock clock
    ) {
        this.jdbc = jdbc;
        this.communities = communities;
        this.relations = relations;
        this.outbox = outbox;
        this.reactions = reactions;
        this.clock = clock;
    }

    public record CommentView(
            UUID id, UUID postId, UUID parentId, UUID replyToUserId,
            UUID authorId, String authorUsername, String authorDisplayName, UUID authorAvatarMediaId,
            String body, boolean deleted, boolean edited, int depth,
            Instant createdAt, Instant updatedAt, long version,
            Map<String, Long> reactionCounts, String viewerReaction
    ) {
    }

    @Transactional
    public UUID create(UUID viewerId, UUID postId, UUID parentId, String rawBody) {
        String body = CommentRules.normalizeBody(rawBody);
        requireViewablePost(postId, viewerId);
        int depth;
        UUID effectiveParentId;
        UUID replyToUserId;
        if (parentId == null) {
            depth = 1;
            effectiveParentId = null;
            replyToUserId = null;
        } else {
            ParentInfo parent = requireParent(postId, parentId);
            if (parent.depth() < CommentRules.MAX_DEPTH) {
                depth = parent.depth() + 1;
                effectiveParentId = parent.id();
            } else {
                depth = CommentRules.MAX_DEPTH;
                effectiveParentId = parent.parentId();
            }
            replyToUserId = parent.authorId();
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO comments (id, post_id, author_id, parent_id, reply_to_user_id, depth, body, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, postId, viewerId, effectiveParentId, replyToUserId, depth, body, Timestamp.from(now), Timestamp.from(now));
        Map<String, Object> payload = new HashMap<>();
        payload.put("commentId", id.toString());
        payload.put("postId", postId.toString());
        payload.put("authorId", viewerId.toString());
        if (replyToUserId != null) {
            payload.put("replyToUserId", replyToUserId.toString());
        }
        outbox.record("post", postId, EVENT_CREATED, payload);
        return id;
    }

    @Transactional
    public void update(UUID commentId, UUID authorId, long expectedVersion, String rawBody) {
        String body = CommentRules.normalizeBody(rawBody);
        int updated = jdbc.update(
                "UPDATE comments SET body = ?, version = version + 1, updated_at = ? "
                        + "WHERE id = ? AND author_id = ? AND deleted_at IS NULL AND version = ?",
                body, Timestamp.from(clock.instant()), commentId, authorId, expectedVersion);
        if (updated == 0) {
            diagnoseUpdateFailure(commentId, authorId);
        }
    }

    @Transactional
    public void delete(UUID commentId, UUID authorId) {
        int updated = jdbc.update(
                "UPDATE comments SET deleted_at = ?, body = NULL, version = version + 1, updated_at = ? "
                        + "WHERE id = ? AND author_id = ? AND deleted_at IS NULL",
                Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), commentId, authorId);
        if (updated == 0) {
            diagnoseDeleteFailure(commentId, authorId);
        }
    }

    /**
     * Корневые комментарии постранично (от старых к новым); у каждого корня в этом же ответе —
     * все его ответы второго и третьего уровня. Глубина ограничена тремя, поэтому это дёшево.
     */
    @Transactional(readOnly = true)
    public CursorPage<CommentView> list(UUID viewerId, UUID postId, String cursor, Integer limit) {
        requireViewablePost(postId, viewerId);
        int size = PageSize.limit(limit);
        Keyset key = decode(cursor);
        List<Row> rootRows = key == null
                ? jdbc.query(rootSql(false), ROW_MAPPER, postId, size + 1)
                : jdbc.query(rootSql(true), ROW_MAPPER, postId, Timestamp.from(key.at()), key.id(), size + 1);
        boolean more = rootRows.size() > size;
        List<Row> pageRoots = more ? rootRows.subList(0, size) : rootRows;
        List<CommentView> items = new ArrayList<>();
        if (!pageRoots.isEmpty()) {
            List<UUID> rootIds = pageRoots.stream().map(Row::id).toList();
            List<Row> level2 = rowsByParentIds(rootIds);
            List<UUID> level2Ids = level2.stream().map(Row::id).toList();
            List<Row> level3 = level2Ids.isEmpty() ? List.of() : rowsByParentIds(level2Ids);
            List<UUID> allIds = new ArrayList<>(rootIds);
            allIds.addAll(level2Ids);
            allIds.addAll(level3.stream().map(Row::id).toList());
            Map<UUID, ReactionService.ReactionSummary> reactionsByComment = reactions.commentReactions(allIds, viewerId);
            for (Row row : pageRoots) {
                items.add(toView(row, reactionsByComment));
            }
            for (Row row : level2) {
                items.add(toView(row, reactionsByComment));
            }
            for (Row row : level3) {
                items.add(toView(row, reactionsByComment));
            }
        }
        String next = more
                ? encode(pageRoots.get(pageRoots.size() - 1).createdAt(), pageRoots.get(pageRoots.size() - 1).id())
                : null;
        return new CursorPage<>(items, next, more);
    }

    /** Видимость обсуждения совпадает с публикацией: удалена/не опубликована — не найдено; группа требует членства. */
    private PostRow requireViewablePost(UUID postId, UUID viewerId) {
        List<PostRow> rows = jdbc.query(
                "SELECT author_id, group_id, status FROM posts WHERE id = ? AND deleted_at IS NULL",
                (rs, n) -> new PostRow(
                        UUID.fromString(rs.getString("author_id")),
                        rs.getObject("group_id") == null ? null : UUID.fromString(rs.getString("group_id")),
                        rs.getString("status")),
                postId);
        if (rows.isEmpty() || !"PUBLISHED".equals(rows.get(0).status())) {
            throw ApiException.notFound();
        }
        PostRow post = rows.get(0);
        if (!post.authorId().equals(viewerId) && relations.blockedBetween(viewerId, post.authorId())) {
            throw ApiException.notFound();
        }
        if (post.groupId() != null && !communities.isActiveMember(post.groupId(), viewerId)) {
            throw ApiException.forbidden();
        }
        return post;
    }

    /** Родитель может быть удалённым (заглушка не обрывает ветку) — проверяется лишь принадлежность посту. */
    private ParentInfo requireParent(UUID postId, UUID parentId) {
        List<ParentInfo> rows = jdbc.query(
                "SELECT id, parent_id, author_id, depth FROM comments WHERE id = ? AND post_id = ?",
                (rs, n) -> new ParentInfo(
                        UUID.fromString(rs.getString("id")),
                        rs.getObject("parent_id") == null ? null : UUID.fromString(rs.getString("parent_id")),
                        UUID.fromString(rs.getString("author_id")),
                        rs.getInt("depth")),
                parentId, postId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        return rows.get(0);
    }

    private List<Row> rowsByParentIds(List<UUID> parentIds) {
        if (parentIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", parentIds.stream().map(id -> "?").toList());
        String sql = SELECT_BASE + "WHERE c.parent_id IN (" + placeholders + ") ORDER BY c.created_at, c.id";
        return jdbc.query(sql, ROW_MAPPER, parentIds.toArray());
    }

    private CommentView toView(Row row, Map<UUID, ReactionService.ReactionSummary> reactionsByComment) {
        boolean deleted = row.deletedAt() != null;
        boolean edited = !deleted && row.updatedAt().isAfter(row.createdAt());
        ReactionService.ReactionSummary summary = deleted
                ? new ReactionService.ReactionSummary(Map.of(), null)
                : reactionsByComment.getOrDefault(row.id(), new ReactionService.ReactionSummary(Map.of(), null));
        return new CommentView(
                row.id(), row.postId(), row.parentId(), row.replyToUserId(),
                row.authorId(), row.authorUsername(), row.authorDisplayName(), row.authorAvatarMediaId(),
                deleted ? null : row.body(), deleted, edited, row.depth(),
                row.createdAt(), row.updatedAt(), row.version(),
                summary.counts(), summary.viewerReaction());
    }

    private void diagnoseUpdateFailure(UUID commentId, UUID authorId) {
        List<Boolean> rows = jdbc.query(
                "SELECT deleted_at IS NOT NULL AS deleted FROM comments WHERE id = ? AND author_id = ?",
                (rs, n) -> rs.getBoolean("deleted"), commentId, authorId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        if (rows.get(0)) {
            throw new ApiException(HttpStatus.CONFLICT, "comment_deleted", "Комментарий удалён", List.of(), null);
        }
        throw new ApiException(HttpStatus.CONFLICT, "version_conflict", "Комментарий изменён в другом месте", List.of(), null);
    }

    private void diagnoseDeleteFailure(UUID commentId, UUID authorId) {
        List<Boolean> rows = jdbc.query(
                "SELECT true FROM comments WHERE id = ? AND author_id = ?", (rs, n) -> true, commentId, authorId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        throw new ApiException(HttpStatus.CONFLICT, "already_deleted", "Комментарий уже удалён", List.of(), null);
    }

    private static String rootSql(boolean hasCursor) {
        return SELECT_BASE + "WHERE c.post_id = ? AND c.parent_id IS NULL "
                + (hasCursor ? "AND (c.created_at, c.id) > (?, ?) " : "")
                + "ORDER BY c.created_at, c.id LIMIT ?";
    }

    private static final String SELECT_BASE = "SELECT c.id, c.post_id, c.parent_id, c.reply_to_user_id, c.depth, c.body, "
            + "c.deleted_at, c.created_at, c.updated_at, c.version, c.author_id, "
            + "u.username AS author_username, pr.display_name AS author_display_name, pr.avatar_media_id AS author_avatar_media_id "
            + "FROM comments c JOIN users u ON u.id = c.author_id JOIN user_profiles pr ON pr.user_id = u.id ";

    private record PostRow(UUID authorId, UUID groupId, String status) {
    }

    private record ParentInfo(UUID id, UUID parentId, UUID authorId, int depth) {
    }

    private record Row(
            UUID id, UUID postId, UUID parentId, UUID replyToUserId, int depth, String body, Instant deletedAt,
            Instant createdAt, Instant updatedAt, long version, UUID authorId,
            String authorUsername, String authorDisplayName, UUID authorAvatarMediaId
    ) {
    }

    private static final RowMapper<Row> ROW_MAPPER = (rs, n) -> new Row(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("post_id")),
            rs.getObject("parent_id") == null ? null : UUID.fromString(rs.getString("parent_id")),
            rs.getObject("reply_to_user_id") == null ? null : UUID.fromString(rs.getString("reply_to_user_id")),
            rs.getInt("depth"),
            rs.getString("body"),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version"),
            UUID.fromString(rs.getString("author_id")),
            rs.getString("author_username"),
            rs.getString("author_display_name"),
            rs.getObject("author_avatar_media_id") == null ? null : UUID.fromString(rs.getString("author_avatar_media_id")));

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
