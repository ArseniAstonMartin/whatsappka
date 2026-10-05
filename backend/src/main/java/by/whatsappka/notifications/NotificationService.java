package by.whatsappka.notifications;

import by.whatsappka.platform.web.ApiException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Создание, прочтение и предпочтения уведомлений. Создание уважает предпочтения получателя. */
@Service
public class NotificationService {

    public record Preference(NotificationType type, boolean enabled, boolean canDisable) {
    }

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Создаёт уведомление, если получатель не действие самого себя и не отключил этот тип.
     * Возвращает id только при фактической записи: повтор того же события (ключ eventKey) ничего не создаёт.
     */
    @Transactional
    public Optional<UUID> notify(UUID recipient, String eventKey, NotificationType type, UUID actor,
                                 NotificationType.TargetKind kind, UUID targetId) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("event key is required");
        }
        NotificationRules.requireValidTarget(recipient, type, actor, kind, targetId);
        if (NotificationRules.isSelf(recipient, actor) || !enabled(recipient, type)) {
            return Optional.empty();
        }
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update(NotificationSql.INSERT_NOTIFICATION, id, recipient, eventKey, type.name(),
                actor, kind == null ? null : kind.name(), targetId);
        return inserted == 1 ? Optional.of(id) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<Preference> preferences(UUID viewer) {
        Map<NotificationType, Boolean> stored = new EnumMap<>(NotificationType.class);
        jdbc.query(NotificationSql.PREFERENCES_OF, (rs) -> {
            stored.put(NotificationRules.parseType(rs.getString("type")), rs.getBoolean("enabled"));
        }, viewer);
        return Arrays.stream(NotificationType.values())
                .map((type) -> new Preference(type, stored.getOrDefault(type, true), type.canDisable()))
                .toList();
    }

    @Transactional
    public Preference setPreference(UUID viewer, NotificationType type, boolean enabled) {
        NotificationRules.requireCanSet(type, enabled);
        jdbc.update(NotificationSql.UPSERT_PREFERENCE, viewer, type.name(), enabled);
        return new Preference(type, enabled, type.canDisable());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID viewer) {
        Long count = jdbc.queryForObject(NotificationSql.UNREAD_COUNT, Long.class, viewer);
        return count == null ? 0 : count;
    }

    /** Чужое или несуществующее уведомление — 404: собственные видны только получателю. */
    @Transactional
    public void markRead(UUID viewer, UUID notificationId) {
        if (jdbc.update(NotificationSql.MARK_READ, notificationId, viewer) == 0) {
            throw ApiException.notFound();
        }
    }

    @Transactional
    public int markAllRead(UUID viewer) {
        return jdbc.update(NotificationSql.MARK_ALL_READ, viewer);
    }

    private boolean enabled(UUID recipient, NotificationType type) {
        List<Boolean> rows = jdbc.query(NotificationSql.PREFERENCE_ENABLED, (rs, n) -> rs.getBoolean(1), recipient, type.name());
        return rows.isEmpty() || rows.get(0);
    }
}
