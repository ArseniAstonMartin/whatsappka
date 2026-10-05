package by.whatsappka.notifications;

/** Типы уведомлений. Для каждого задан вид объекта, на который ссылка; системные отключить нельзя. */
public enum NotificationType {
    FOLLOW(TargetKind.USER),
    MESSAGE(TargetKind.CONVERSATION),
    CHAT_INVITATION(TargetKind.CONVERSATION),
    COMMUNITY_INVITATION(TargetKind.COMMUNITY),
    JOIN_REQUEST(TargetKind.COMMUNITY),
    SYSTEM(null);

    public enum TargetKind { USER, CONVERSATION, COMMUNITY }

    private final TargetKind targetKind;

    NotificationType(TargetKind targetKind) {
        this.targetKind = targetKind;
    }

    public TargetKind targetKind() {
        return targetKind;
    }

    public boolean canDisable() {
        return this != SYSTEM;
    }
}
