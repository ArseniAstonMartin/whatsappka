package by.whatsappka.content;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Публичная карточка опубликованного поста: лента, профиль, группа, хештег (TASK-044, TASK-045, TASK-047).
 * {@code updatedAt} отличается от {@code publishedAt}, когда запись правили после публикации — отметка
 * «Изменено» в интерфейсе строится по этому сравнению, отдельного флага сервер не хранит.
 */
public record PostSummaryPublic(
        UUID id, String body, UUID authorId, String authorUsername, String authorDisplayName, UUID authorAvatarMediaId,
        UUID groupId, List<UUID> mediaIds, List<String> hashtags, Instant publishedAt, Instant updatedAt,
        Map<String, Long> reactionCounts, String viewerReaction
) {
}
