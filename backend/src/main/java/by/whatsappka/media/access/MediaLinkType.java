package by.whatsappka.media.access;

import by.whatsappka.media.MediaPurpose;
import java.util.Set;

/** Типы привязок. Для каждого задано, какие назначения файла к нему допустимы, и может ли клиент привязать сам. */
public enum MediaLinkType {
    PROFILE_AVATAR(Set.of(MediaPurpose.AVATAR), true),
    PROFILE_COVER(Set.of(MediaPurpose.COVER), true),
    GROUP_AVATAR(Set.of(MediaPurpose.AVATAR), true),
    /** Ставится только отправкой сообщения: назначение и принадлежность проверяет отправка. */
    CHAT_ATTACHMENT(Set.of(MediaPurpose.CHAT_IMAGE, MediaPurpose.CHAT_DOCUMENT), false),
    /** Аватар и обложка сообщества (не группового чата): назначение проверяет CommunityService, не клиент. */
    COMMUNITY_AVATAR(Set.of(MediaPurpose.AVATAR), false),
    COMMUNITY_COVER(Set.of(MediaPurpose.COVER), false),
    /** Изображение публикации: назначение и принадлежность проверяет PostService, не клиент. */
    POST_ATTACHMENT(Set.of(MediaPurpose.POST_IMAGE), false);

    private final Set<MediaPurpose> purposes;
    private final boolean clientAttachable;

    MediaLinkType(Set<MediaPurpose> purposes, boolean clientAttachable) {
        this.purposes = purposes;
        this.clientAttachable = clientAttachable;
    }

    public boolean accepts(String purposeName) {
        return purposes.stream().anyMatch(purpose -> purpose.name().equals(purposeName));
    }

    public boolean clientAttachable() {
        return clientAttachable;
    }
}
