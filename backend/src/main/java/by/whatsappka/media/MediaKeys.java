package by.whatsappka.media;

import java.util.UUID;

/**
 * Ключи объектов в bucket. Ключ строится только сервером из id владельца и медиа:
 * имя файла от клиента в ключ не попадает, и путь нельзя подобрать с чужим владельцем.
 */
public final class MediaKeys {

    private MediaKeys() {
    }

    public static String original(UUID ownerId, UUID mediaId) {
        return "users/" + ownerId + "/media/" + mediaId + "/original";
    }

    public static String variant(UUID ownerId, UUID mediaId, VariantKind kind) {
        return "users/" + ownerId + "/media/" + mediaId + "/variants/" + kind.maxSide();
    }
}
