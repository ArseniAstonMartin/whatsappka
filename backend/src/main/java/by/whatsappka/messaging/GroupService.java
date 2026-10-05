package by.whatsappka.messaging;

import by.whatsappka.media.access.MediaLinkType;
import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import by.whatsappka.messaging.GroupRules.Role;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Групповой чат: создание, состав и роли. Все изменения идут под блокировкой чата, поэтому передача владения,
 * смена ролей и лимит участников не расходятся при параллельных запросах.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GroupService {

    static final String EVENT_CREATED = "group.created";
    static final String EVENT_MEMBER_JOINED = "member.joined";
    static final String EVENT_MEMBER_LEFT = "member.left";
    static final String EVENT_MEMBER_REMOVED = "member.removed";
    static final String EVENT_ROLE_CHANGED = "role.changed";
    static final String EVENT_OWNER_TRANSFERRED = "owner.transferred";
    static final String EVENT_AVATAR_CHANGED = "avatar.changed";
    static final String EVENT_TITLE_CHANGED = "title.changed";

    private static final int TITLE_MAX = 100;

    private final JdbcTemplate jdbc;
    private final OutboxWriter outbox;
    private final Clock clock;

    public GroupService(JdbcTemplate jdbc, OutboxWriter outbox, Clock clock) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public UUID create(UUID ownerId, String rawTitle, UUID avatarMediaId) {
        String title = normalizeTitle(rawTitle);
        UUID id = UUID.randomUUID();
        jdbc.update(GroupSql.INSERT_GROUP, id, title, ownerId);
        jdbc.update(GroupSql.INSERT_MEMBERSHIP, UUID.randomUUID(), id, ownerId, Role.MEMBER.name(), id);
        event(id, EVENT_CREATED, ownerId, ownerId);
        if (avatarMediaId != null) {
            setAvatar(id, ownerId, avatarMediaId);
        }
        return id;
    }

    /**
     * Вступление после принятия приглашения (TASK-054). Прямого добавления без согласия здесь нет.
     */
    @Transactional
    public void join(UUID conversationId, UUID userId) {
        lock(conversationId);
        if (activeRole(conversationId, userId) != null) {
            return;
        }
        if (!GroupRules.hasRoomFor(activeCount(conversationId))) {
            throw new ApiException(HttpStatus.CONFLICT, "chat_full", "В чате уже максимальное число участников",
                    List.of(), null);
        }
        jdbc.update(GroupSql.INSERT_MEMBERSHIP, UUID.randomUUID(), conversationId, userId, Role.MEMBER.name(), conversationId);
        event(conversationId, EVENT_MEMBER_JOINED, userId, userId);
    }

    @Transactional
    public void leave(UUID conversationId, UUID userId) {
        lock(conversationId);
        Role role = requireMember(conversationId, userId);
        if (!GroupRules.canLeave(role)) {
            throw new ApiException(HttpStatus.CONFLICT, "owner_must_transfer",
                    "Владелец не может выйти без передачи владения", List.of(), null);
        }
        jdbc.update(GroupSql.CLOSE_MEMBERSHIP, conversationId, userId);
        event(conversationId, EVENT_MEMBER_LEFT, userId, userId);
    }

    @Transactional
    public void remove(UUID conversationId, UUID actorId, UUID targetId) {
        lock(conversationId);
        Role actor = requireMember(conversationId, actorId);
        Role target = requireMember(conversationId, targetId);
        if (!GroupRules.canRemove(actor, target)) {
            throw ApiException.forbidden();
        }
        jdbc.update(GroupSql.CLOSE_MEMBERSHIP, conversationId, targetId);
        event(conversationId, EVENT_MEMBER_REMOVED, actorId, targetId);
    }

    @Transactional
    public void setRole(UUID conversationId, UUID actorId, UUID targetId, String rawRole) {
        lock(conversationId);
        Role actor = requireMember(conversationId, actorId);
        Role target = requireMember(conversationId, targetId);
        Role role = parseAssignable(rawRole);
        if (!GroupRules.canSetRole(actor, target)) {
            throw ApiException.forbidden();
        }
        jdbc.update(GroupSql.SET_ROLE, role.name(), conversationId, targetId);
        event(conversationId, EVENT_ROLE_CHANGED, actorId, targetId);
    }

    /** Владение переходит атомарно: новый владелец назначен, прежний становится администратором в той же транзакции. */
    @Transactional
    public void transferOwnership(UUID conversationId, UUID actorId, UUID targetId) {
        lock(conversationId);
        Role actor = requireMember(conversationId, actorId);
        requireMember(conversationId, targetId);
        if (!GroupRules.canTransferOwnership(actor) || targetId.equals(actorId)) {
            throw ApiException.forbidden();
        }
        jdbc.update(GroupSql.SET_OWNER, targetId, conversationId);
        jdbc.update(GroupSql.SET_ROLE, Role.ADMIN.name(), conversationId, actorId);
        jdbc.update(GroupSql.SET_ROLE, Role.MEMBER.name(), conversationId, targetId);
        event(conversationId, EVENT_OWNER_TRANSFERRED, actorId, targetId);
    }

    @Transactional
    public void changeAvatar(UUID conversationId, UUID actorId, UUID mediaId) {
        lock(conversationId);
        Role actor = requireMember(conversationId, actorId);
        if (!GroupRules.canEditSettings(actor)) {
            throw ApiException.forbidden();
        }
        setAvatar(conversationId, actorId, mediaId);
    }

    @Transactional
    public void rename(UUID conversationId, UUID actorId, String rawTitle) {
        lock(conversationId);
        Role actor = requireMember(conversationId, actorId);
        if (!GroupRules.canEditSettings(actor)) {
            throw ApiException.forbidden();
        }
        String title = normalizeTitle(rawTitle);
        jdbc.update(GroupSql.SET_TITLE, title, conversationId);
        event(conversationId, EVENT_TITLE_CHANGED, actorId, null);
    }

    private void setAvatar(UUID conversationId, UUID actorId, UUID mediaId) {
        Integer ok = jdbc.queryForObject(
                "SELECT count(*) FROM media_assets WHERE id = ? AND owner_id = ? AND status = 'READY' "
                        + "AND purpose = 'AVATAR' AND deleted_at IS NULL",
                Integer.class, mediaId, actorId);
        if (ok == null || ok == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "purpose_mismatch",
                    "Аватар группы — готовый собственный файл назначения AVATAR", List.of(), null);
        }
        jdbc.update("DELETE FROM media_links WHERE link_type = ? AND link_id = ?", MediaLinkType.GROUP_AVATAR.name(), conversationId);
        jdbc.update("INSERT INTO media_links (media_id, link_type, link_id, created_at) VALUES (?, ?, ?, ?)",
                mediaId, MediaLinkType.GROUP_AVATAR.name(), conversationId, Timestamp.from(clock.instant()));
        jdbc.update(GroupSql.SET_AVATAR, mediaId, conversationId);
        event(conversationId, EVENT_AVATAR_CHANGED, actorId, null);
    }

    void lock(UUID conversationId) {
        jdbc.query(GroupSql.LOCK_CONVERSATION, rs -> { }, conversationId);
        Object owner = jdbc.query(GroupSql.OWNER_OF, rs -> rs.next() ? rs.getObject(1) : null, conversationId);
        if (owner == null) {
            throw ApiException.notFound();
        }
    }

    private Role requireMember(UUID conversationId, UUID userId) {
        Role role = activeRole(conversationId, userId);
        if (role == null) {
            throw ApiException.notFound();
        }
        return role;
    }

    /** Роль участника с учётом владельца: владелец хранится у чата, а не в строке членства. */
    Role activeRole(UUID conversationId, UUID userId) {
        UUID owner = jdbc.query(GroupSql.OWNER_OF, rs -> rs.next() ? (UUID) rs.getObject(1) : null, conversationId);
        if (owner != null && owner.equals(userId)) {
            return Role.OWNER;
        }
        List<String> roles = jdbc.queryForList(GroupSql.ACTIVE_ROLE, String.class, conversationId, userId);
        return roles.isEmpty() ? null : Role.valueOf(roles.get(0));
    }

    int activeCount(UUID conversationId) {
        Long count = jdbc.queryForObject(GroupSql.COUNT_ACTIVE, Long.class, conversationId);
        return count == null ? 0 : count.intValue();
    }

    private void event(UUID conversationId, String type, UUID actorId, UUID subjectId) {
        Long seq = jdbc.queryForObject(GroupSql.NEXT_EVENT_SEQ, Long.class, conversationId);
        jdbc.update(GroupSql.INSERT_EVENT, UUID.randomUUID(), conversationId, seq, type, actorId, subjectId);
        outbox.record("conversation", conversationId, "conversation." + type, Map.of(
                "conversationId", conversationId.toString(),
                "eventSeq", seq));
    }

    private static String normalizeTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        if (title.isEmpty() || title.length() > TITLE_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("title", "Название от 1 до 100 символов")));
        }
        return title;
    }

    private static Role parseAssignable(String raw) {
        if ("ADMIN".equals(raw)) {
            return Role.ADMIN;
        }
        if ("MEMBER".equals(raw)) {
            return Role.MEMBER;
        }
        throw ApiException.validation("Проверьте поля запроса",
                List.of(new FieldErrorDetail("role", "Допустимы ADMIN или MEMBER")));
    }
}
