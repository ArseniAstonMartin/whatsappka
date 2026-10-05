package by.whatsappka.content;

import by.whatsappka.communities.CommunityMembershipService;
import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.social.SocialRelations;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Реакции на посты и комментарии (TASK-050, FR-05). PUT идемпотентно задаёт тип: повтор того же
 * типа не трогает строку и не создаёт событие — WHERE в ON CONFLICT отсекает это прямо в БД,
 * поэтому гонка двух одинаковых запросов не даёт дубль и не удваивает уведомление. DELETE снимает
 * реакцию. Автор не получает outbox-событие о собственной реакции на свой же объект.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ReactionService {

    static final String EVENT_POST_REACTED = "post.reacted";
    static final String EVENT_COMMENT_REACTED = "comment.reacted";

    private final JdbcTemplate jdbc;
    private final CommunityMembershipService communities;
    private final SocialRelations relations;
    private final OutboxWriter outbox;
    private final Clock clock;

    public ReactionService(
            JdbcTemplate jdbc, CommunityMembershipService communities, SocialRelations relations,
            OutboxWriter outbox, Clock clock
    ) {
        this.jdbc = jdbc;
        this.communities = communities;
        this.relations = relations;
        this.outbox = outbox;
        this.clock = clock;
    }

    public record ReactionSummary(Map<String, Long> counts, String viewerReaction) {
    }

    @Transactional
    public void setPostReaction(UUID viewerId, UUID postId, String rawType) {
        String type = ReactionRules.requireType(rawType);
        UUID authorId = requireViewablePost(postId, viewerId);
        int changed = jdbc.update(
                "INSERT INTO post_reactions (post_id, user_id, type, created_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (post_id, user_id) DO UPDATE SET type = EXCLUDED.type, created_at = EXCLUDED.created_at "
                        + "WHERE post_reactions.type <> EXCLUDED.type",
                postId, viewerId, type, Timestamp.from(clock.instant()));
        if (changed > 0 && !authorId.equals(viewerId)) {
            outbox.record("post", postId, EVENT_POST_REACTED, Map.of(
                    "postId", postId.toString(), "actorId", viewerId.toString(),
                    "authorId", authorId.toString(), "type", type));
        }
    }

    @Transactional
    public void removePostReaction(UUID viewerId, UUID postId) {
        requireViewablePost(postId, viewerId);
        jdbc.update("DELETE FROM post_reactions WHERE post_id = ? AND user_id = ?", postId, viewerId);
    }

    @Transactional
    public void setCommentReaction(UUID viewerId, UUID commentId, String rawType) {
        String type = ReactionRules.requireType(rawType);
        UUID authorId = requireViewableComment(commentId, viewerId);
        int changed = jdbc.update(
                "INSERT INTO comment_reactions (comment_id, user_id, type, created_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (comment_id, user_id) DO UPDATE SET type = EXCLUDED.type, created_at = EXCLUDED.created_at "
                        + "WHERE comment_reactions.type <> EXCLUDED.type",
                commentId, viewerId, type, Timestamp.from(clock.instant()));
        if (changed > 0 && !authorId.equals(viewerId)) {
            outbox.record("comment", commentId, EVENT_COMMENT_REACTED, Map.of(
                    "commentId", commentId.toString(), "actorId", viewerId.toString(),
                    "authorId", authorId.toString(), "type", type));
        }
    }

    @Transactional
    public void removeCommentReaction(UUID viewerId, UUID commentId) {
        requireViewableComment(commentId, viewerId);
        jdbc.update("DELETE FROM comment_reactions WHERE comment_id = ? AND user_id = ?", commentId, viewerId);
    }

    /** Счётчики и реакция зрителя для одного поста — используется detail-страницей. */
    @Transactional(readOnly = true)
    public ReactionSummary postReactions(UUID postId, UUID viewerId) {
        Map<String, Long> counts = countsByType(
                "SELECT type, count(*) AS c FROM post_reactions WHERE post_id = ? GROUP BY type", postId);
        String mine = jdbc.query("SELECT type FROM post_reactions WHERE post_id = ? AND user_id = ?",
                (rs, n) -> rs.getString("type"), postId, viewerId).stream().findFirst().orElse(null);
        return new ReactionSummary(counts, mine);
    }

    /** То же самое пакетно для списка комментариев — без запроса на каждую строку. */
    @Transactional(readOnly = true)
    public Map<UUID, ReactionSummary> commentReactions(List<UUID> commentIds, UUID viewerId) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", commentIds.stream().map(id -> "?").toList());
        List<Object> countParams = new ArrayList<>(commentIds);
        List<CommentTypeCount> countRows = jdbc.query(
                "SELECT comment_id, type, count(*) AS c FROM comment_reactions WHERE comment_id IN (" + placeholders + ") "
                        + "GROUP BY comment_id, type",
                (rs, n) -> new CommentTypeCount(
                        UUID.fromString(rs.getString("comment_id")), rs.getString("type"), rs.getLong("c")),
                countParams.toArray());
        List<Object> mineParams = new ArrayList<>(commentIds);
        mineParams.add(viewerId);
        List<CommentTypeCount> mineRows = jdbc.query(
                "SELECT comment_id, type FROM comment_reactions WHERE comment_id IN (" + placeholders + ") AND user_id = ?",
                (rs, n) -> new CommentTypeCount(UUID.fromString(rs.getString("comment_id")), rs.getString("type"), 0),
                mineParams.toArray());
        Map<UUID, Map<String, Long>> countsByComment = new HashMap<>();
        for (CommentTypeCount row : countRows) {
            countsByComment.computeIfAbsent(row.commentId(), k -> new LinkedHashMap<>()).put(row.type(), row.count());
        }
        Map<UUID, String> mineByComment = new HashMap<>();
        for (CommentTypeCount row : mineRows) {
            mineByComment.put(row.commentId(), row.type());
        }
        Map<UUID, ReactionSummary> result = new HashMap<>();
        for (UUID id : commentIds) {
            Map<String, Long> counts = countsByComment.get(id);
            result.put(id, counts == null
                    ? new ReactionSummary(Map.of(), mineByComment.get(id))
                    : new ReactionSummary(counts, mineByComment.get(id)));
        }
        return result;
    }

    private Map<String, Long> countsByType(String sql, UUID id) {
        List<CommentTypeCount> rows = jdbc.query(sql,
                (rs, n) -> new CommentTypeCount(null, rs.getString("type"), rs.getLong("c")), id);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (CommentTypeCount row : rows) {
            counts.put(row.type(), row.count());
        }
        return counts;
    }

    /** Та же видимость, что и у обсуждения (FR-05): удалён/не опубликован — не найдено, группа требует членства. */
    private UUID requireViewablePost(UUID postId, UUID viewerId) {
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
        return post.authorId();
    }

    /** Реакция недоступна на заглушку удалённого комментария; видимость наследуется от поста. */
    private UUID requireViewableComment(UUID commentId, UUID viewerId) {
        List<CommentRow> rows = jdbc.query(
                "SELECT post_id, author_id FROM comments WHERE id = ? AND deleted_at IS NULL",
                (rs, n) -> new CommentRow(
                        UUID.fromString(rs.getString("post_id")), UUID.fromString(rs.getString("author_id"))),
                commentId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        CommentRow comment = rows.get(0);
        requireViewablePost(comment.postId(), viewerId);
        return comment.authorId();
    }

    private record PostRow(UUID authorId, UUID groupId, String status) {
    }

    private record CommentRow(UUID postId, UUID authorId) {
    }

    private record CommentTypeCount(UUID commentId, String type, long count) {
    }
}
