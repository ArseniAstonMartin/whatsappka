package by.whatsappka.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Переводит событие outbox в список уведомлений. Чистая функция: все данные, которых нет в payload,
 * приходят через Lookups, поэтому правила проверяются без базы. Неизвестные события ничего не порождают.
 */
public final class NotificationPlanner {

    /** Источник данных для событий. Пустой ответ означает: уведомление не создаётся. */
    public interface Lookups {
        /** Автор опубликованного, не удалённого поста. */
        UUID postAuthor(UUID postId);

        /** Пригласивший в чат, пока приглашение существует. */
        Optional<UUID> chatInviter(UUID invitationId);

        /** Пригласивший в сообщество. */
        Optional<UUID> communityInviter(UUID invitationId);

        /** Владелец и администраторы сообщества. */
        List<UUID> communityAdmins(UUID groupId);

        /** Отправитель и seq неудалённого сообщения. */
        Optional<MessageRef> message(UUID messageId);

        /** Действующие участники чата, которые уже были в нём на момент seq, кроме отправителя. */
        List<UUID> messageRecipients(UUID conversationId, long seq, UUID sender);
    }

    public record MessageRef(UUID sender, long seq, UUID conversationId) {
    }

    public record Planned(UUID recipient, NotificationType type, UUID actor,
                          NotificationType.TargetKind kind, UUID targetId, Long messageSeq) {
    }

    private NotificationPlanner() {
    }

    public static List<Planned> plan(String eventType, JsonNode payload, Lookups lookups) {
        return switch (eventType) {
            case "follow.created" -> List.of(new Planned(
                    uuid(payload, "followeeId"), NotificationType.FOLLOW, uuid(payload, "followerId"),
                    NotificationType.TargetKind.USER, uuid(payload, "followeeId"), null));
            case "post.reacted" -> List.of(new Planned(
                    uuid(payload, "authorId"), NotificationType.REACTION, uuid(payload, "actorId"),
                    NotificationType.TargetKind.POST, uuid(payload, "postId"), null));
            case "comment.reacted" -> List.of(new Planned(
                    uuid(payload, "authorId"), NotificationType.REACTION, uuid(payload, "actorId"),
                    NotificationType.TargetKind.COMMENT, uuid(payload, "commentId"), null));
            case "comment.created" -> commentCreated(payload, lookups);
            case "message.created" -> messageCreated(payload, lookups);
            case "conversation.invitation.created" -> chatInvitation(payload, lookups);
            case "group.invitation_created" -> communityInvitation(payload, lookups);
            case "group.join_request_created" -> joinRequest(payload, lookups);
            case "group.join_request_accepted", "group.join_request_rejected" -> List.of(new Planned(
                    uuid(payload, "requesterId"), NotificationType.JOIN_RESULT, null,
                    NotificationType.TargetKind.COMMUNITY, uuid(payload, "groupId"), null));
            default -> List.of();
        };
    }

    /** Новое сообщение: каждому действующему участнику, который уже был в чате, кроме отправителя. */
    private static List<Planned> messageCreated(JsonNode payload, Lookups lookups) {
        UUID messageId = uuid(payload, "messageId");
        Optional<MessageRef> message = lookups.message(messageId);
        if (message.isEmpty()) {
            return List.of();
        }
        MessageRef ref = message.get();
        List<Planned> planned = new ArrayList<>();
        for (UUID recipient : lookups.messageRecipients(ref.conversationId(), ref.seq(), ref.sender())) {
            planned.add(new Planned(recipient, NotificationType.MESSAGE, ref.sender(),
                    NotificationType.TargetKind.CONVERSATION, ref.conversationId(), ref.seq()));
        }
        return planned;
    }

    private static List<Planned> chatInvitation(JsonNode payload, Lookups lookups) {
        UUID invitee = uuid(payload, "inviteeId");
        return lookups.chatInviter(uuid(payload, "invitationId"))
                .map(inviter -> List.of(new Planned(invitee, NotificationType.CHAT_INVITATION, inviter,
                        NotificationType.TargetKind.CONVERSATION, uuid(payload, "conversationId"), null)))
                .orElse(List.of());
    }

    private static List<Planned> communityInvitation(JsonNode payload, Lookups lookups) {
        UUID invitee = uuid(payload, "inviteeId");
        return lookups.communityInviter(uuid(payload, "invitationId"))
                .map(inviter -> List.of(new Planned(invitee, NotificationType.COMMUNITY_INVITATION, inviter,
                        NotificationType.TargetKind.COMMUNITY, uuid(payload, "groupId"), null)))
                .orElse(List.of());
    }

    /** Заявка: администраторам сообщества, кроме самого заявителя. */
    private static List<Planned> joinRequest(JsonNode payload, Lookups lookups) {
        UUID requester = uuid(payload, "requesterId");
        UUID groupId = uuid(payload, "groupId");
        List<Planned> planned = new ArrayList<>();
        for (UUID admin : lookups.communityAdmins(groupId)) {
            if (!admin.equals(requester)) {
                planned.add(new Planned(admin, NotificationType.JOIN_REQUEST, requester,
                        NotificationType.TargetKind.COMMUNITY, groupId, null));
            }
        }
        return planned;
    }

    /**
     * Ответ получает уведомление REPLY; автор поста получает COMMENT, но не дублирует, если он и есть адресат ответа.
     */
    private static List<Planned> commentCreated(JsonNode payload, Lookups lookups) {
        UUID commenter = uuid(payload, "authorId");
        UUID commentId = uuid(payload, "commentId");
        UUID postId = uuid(payload, "postId");
        UUID replyTo = payload.hasNonNull("replyToUserId") ? uuid(payload, "replyToUserId") : null;
        List<Planned> planned = new ArrayList<>();
        if (replyTo != null) {
            planned.add(new Planned(replyTo, NotificationType.REPLY, commenter, NotificationType.TargetKind.COMMENT, commentId, null));
        }
        UUID postAuthor = lookups.postAuthor(postId);
        if (postAuthor != null && !postAuthor.equals(replyTo)) {
            planned.add(new Planned(postAuthor, NotificationType.COMMENT, commenter, NotificationType.TargetKind.POST, postId, null));
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
