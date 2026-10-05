package by.whatsappka.notifications;

import java.util.EnumSet;
import java.util.Set;

/** Типы уведомлений. Для каждого задано, на какие виды объектов может ссылаться; системные отключить нельзя. */
public enum NotificationType {
    FOLLOW(EnumSet.of(TargetKind.USER)),
    MESSAGE(EnumSet.of(TargetKind.CONVERSATION)),
    CHAT_INVITATION(EnumSet.of(TargetKind.CONVERSATION)),
    COMMUNITY_INVITATION(EnumSet.of(TargetKind.COMMUNITY)),
    JOIN_REQUEST(EnumSet.of(TargetKind.COMMUNITY)),
    JOIN_RESULT(EnumSet.of(TargetKind.COMMUNITY)),
    COMMENT(EnumSet.of(TargetKind.POST)),
    REPLY(EnumSet.of(TargetKind.COMMENT)),
    REACTION(EnumSet.of(TargetKind.POST, TargetKind.COMMENT)),
    SYSTEM(EnumSet.noneOf(TargetKind.class));

    public enum TargetKind { USER, CONVERSATION, COMMUNITY, POST, COMMENT }

    private final Set<TargetKind> targets;

    NotificationType(Set<TargetKind> targets) {
        this.targets = targets;
    }

    public boolean allowsTarget(TargetKind kind) {
        return kind != null && targets.contains(kind);
    }

    public boolean canDisable() {
        return this != SYSTEM;
    }
}
