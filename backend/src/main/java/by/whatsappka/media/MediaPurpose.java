package by.whatsappka.media;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/** Назначение файла определяет допустимые форматы и предел размера. */
public enum MediaPurpose {
    AVATAR(5L * 1024 * 1024, Set.of(MediaType.JPEG, MediaType.PNG, MediaType.WEBP)),
    COVER(5L * 1024 * 1024, Set.of(MediaType.JPEG, MediaType.PNG, MediaType.WEBP)),
    POST_IMAGE(10L * 1024 * 1024, Set.of(MediaType.JPEG, MediaType.PNG, MediaType.WEBP)),
    CHAT_IMAGE(10L * 1024 * 1024, Set.of(MediaType.JPEG, MediaType.PNG, MediaType.WEBP)),
    CHAT_DOCUMENT(20L * 1024 * 1024, Set.of(MediaType.PDF, MediaType.TXT));

    private final long maxBytes;
    private final Set<MediaType> allowed;

    MediaPurpose(long maxBytes, Set<MediaType> allowed) {
        this.maxBytes = maxBytes;
        this.allowed = allowed;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public boolean allows(MediaType type) {
        return allowed.contains(type);
    }

    public static Optional<MediaPurpose> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter((p) -> p.name().equals(value)).findFirst();
    }
}
