package by.whatsappka.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Переводит событие outbox в список уведомлений. Чистая функция: данные о посте передаются извне,
 * поэтому правила проверяются без базы. Неизвестные события ничего не порождают.
 */
public final class NotificationPlanner {

    /** Поиск автора поста; null — пост не найден или удалён, уведомление не создаётся. */
    public interface PostAuthors {
        UUID authorOf(UUID postId);
    }

    public record Planned(UUID recipient, NotificationType type, UUID actor,
                          NotificationType.TargetKind kind, UUID targetId) {
    }

    private NotificationPlanner() {
    }

    public static List<Planned> plan(String eventType, JsonNode payload, PostAuthors posts) {
        return switch (eventType) {
            case "follow.created" -> List.of(new Planned(
                    uuid(payload, "followeeId"), NotificationType.FOLLOW, uuid(payload, "followerId"),
                    NotificationType.TargetKind.USER, uuid(payload, "followeeId")));
            case "post.reacted" -> List.of(new Planned(
                    uuid(payload, "authorId"), NotificationType.REACTION, uuid(payload, "actorId"),
                    NotificationType.TargetKind.POST, uuid(payload, "postId")));
            case "comment.reacted" -> List.of(new Planned(
                    uuid(payload, "authorId"), NotificationType.REACTION, uuid(payload, "actorId"),
                    NotificationType.TargetKind.COMMENT, uuid(payload, "commentId")));
            case "comment.created" -> commentCreated(payload, posts);
            default -> List.of();
        };
    }

    /**
     * Ответ получает уведомление REPLY; автор поста получает COMMENT, но не дублирует, если он и есть адресат ответа.
     */
    private static List<Planned> commentCreated(JsonNode payload, PostAuthors posts) {
        UUID commenter = uuid(payload, "authorId");
        UUID commentId = uuid(payload, "commentId");
        UUID postId = uuid(payload, "postId");
        UUID replyTo = payload.hasNonNull("replyToUserId") ? uuid(payload, "replyToUserId") : null;
        List<Planned> planned = new ArrayList<>();
        if (replyTo != null) {
            planned.add(new Planned(replyTo, NotificationType.REPLY, commenter, NotificationType.TargetKind.COMMENT, commentId));
        }
        UUID postAuthor = posts.authorOf(postId);
        if (postAuthor != null && !postAuthor.equals(replyTo)) {
            planned.add(new Planned(postAuthor, NotificationType.COMMENT, commenter, NotificationType.TargetKind.POST, postId));
        }
        return planned;
    }

    private static UUID uuid(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("payload field missing: " + field);
        }
        return UUID.fromString(value.asText());
    }
}
