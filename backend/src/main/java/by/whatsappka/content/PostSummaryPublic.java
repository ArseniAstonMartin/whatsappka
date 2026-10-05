package by.whatsappka.content;

import java.time.Instant;
import java.util.UUID;

/** Публичная карточка опубликованного поста: лента, профиль, группа, хештег (TASK-044, TASK-045). */
public record PostSummaryPublic(UUID id, String body, UUID authorId, UUID groupId, Instant publishedAt) {
}
