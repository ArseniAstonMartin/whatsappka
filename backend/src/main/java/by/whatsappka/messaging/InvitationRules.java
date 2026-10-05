package by.whatsappka.messaging;

import by.whatsappka.messaging.GroupRules.Role;
import java.time.Instant;
import java.util.UUID;

/** Правила приглашений (FR-06): кто приглашает, принимает и отзывает. */
public final class InvitationRules {

    private InvitationRules() {
    }

    /** Приглашать могут владелец и администраторы. Рядовой участник — нет. */
    public static boolean canInvite(Role actor) {
        return actor == Role.OWNER || actor == Role.ADMIN;
    }

    /** Принять и отклонить может только получатель, и только пока приглашение действует. */
    public static boolean isInviteeOfPending(UUID viewer, UUID invitee, String status, Instant expiresAt, Instant now) {
        return viewer.equals(invitee) && "PENDING".equals(status) && expiresAt.isAfter(now);
    }

    /** Отозвать может сам пригласивший, либо владелец и администраторы, пока приглашение ожидает ответа. */
    public static boolean canRevoke(UUID viewer, UUID inviter, Role viewerRole) {
        return viewer.equals(inviter) || canInvite(viewerRole);
    }

    /** Приглашение нельзя принять, если пригласивший больше не вправе приглашать. */
    public static boolean inviterStillEntitled(Role inviterRole) {
        return canInvite(inviterRole);
    }
}
