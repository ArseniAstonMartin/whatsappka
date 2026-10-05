package by.whatsappka.media.access;

import by.whatsappka.media.MediaPurpose;

/** Типы привязок. Для каждого задано назначение файла, которое к нему допустимо. */
public enum MediaLinkType {
    PROFILE_AVATAR(MediaPurpose.AVATAR),
    PROFILE_COVER(MediaPurpose.COVER),
    GROUP_AVATAR(MediaPurpose.AVATAR);

    private final MediaPurpose purpose;

    MediaLinkType(MediaPurpose purpose) {
        this.purpose = purpose;
    }

    public MediaPurpose purpose() {
        return purpose;
    }
}
