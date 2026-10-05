package by.whatsappka.communities;

import java.util.UUID;

/**
 * Безопасные публичные поля сообщества, которые допустимо держать в коротком кэше ({@code PublicFieldsCache}).
 * Видимость, владелец и факт удаления сюда не входят: эти решения всегда проверяются на PostgreSQL.
 */
record CommunityPublicFields(String description, UUID avatarMediaId, UUID coverMediaId) {

    private static final String CACHE_KEY_PREFIX = "cache:group:public:";

    static String cacheKey(UUID groupId) {
        return CACHE_KEY_PREFIX + groupId;
    }
}
