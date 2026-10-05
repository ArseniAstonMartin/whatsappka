package by.whatsappka.messaging;

/**
 * Правила ролей группового чата (PRD FR-06). Владелец не хранится ролью в строке членства,
 * поэтому здесь он отдельное значение.
 */
public final class GroupRules {

    public static final int MAX_ACTIVE_MEMBERS = 100;

    public enum Role { OWNER, ADMIN, MEMBER }

    private GroupRules() {
    }

    /** Исключать может владелец любого, кроме себя; администратор — только рядовых участников. */
    public static boolean canRemove(Role actor, Role target) {
        if (actor == Role.OWNER) {
            return target != Role.OWNER;
        }
        return actor == Role.ADMIN && target == Role.MEMBER;
    }

    /** Назначать и снимать администраторов может только владелец. Владельца это не касается. */
    public static boolean canSetRole(Role actor, Role target) {
        return actor == Role.OWNER && target != Role.OWNER;
    }

    public static boolean canTransferOwnership(Role actor) {
        return actor == Role.OWNER;
    }

    /** Название и аватар меняют владелец и администраторы. */
    public static boolean canEditSettings(Role actor) {
        return actor == Role.OWNER || actor == Role.ADMIN;
    }

    /** Владелец не выходит, пока не передал владение или не закрыл чат. */
    public static boolean canLeave(Role actor) {
        return actor != Role.OWNER;
    }

    public static boolean hasRoomFor(int activeMembers) {
        return activeMembers < MAX_ACTIVE_MEMBERS;
    }
}
