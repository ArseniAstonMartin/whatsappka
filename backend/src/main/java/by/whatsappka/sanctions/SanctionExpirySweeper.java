package by.whatsappka.sanctions;

import by.whatsappka.notifications.NotificationService;
import by.whatsappka.notifications.NotificationType;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Снимает санкции с истёкшим сроком и возвращает аккаунт, если действующих санкций не осталось.
 * Контент не трогается: скрытое и удалённое не возвращается. Безопасно при параллельных запусках:
 * строка санкции блокируется, повторный запуск её уже не находит.
 */
@Component
public class SanctionExpirySweeper {

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public SanctionExpirySweeper(JdbcTemplate jdbc, NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void sweep() {
        List<Expired> expired = jdbc.query(SanctionSql.EXPIRE, (rs, n) ->
                new Expired(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("user_id"))));
        for (Expired row : expired) {
            jdbc.update(SanctionSql.RESTORE_ACCOUNT_IF_FREE, row.userId());
            jdbc.update(SanctionSql.INSERT_AUDIT, UUID.randomUUID(), "SANCTION_EXPIRED", row.userId(), "срок истёк");
            notifications.notify(row.userId(), "sanction:" + row.id() + ":expired", NotificationType.SYSTEM, null, null, null, null);
        }
    }

    private record Expired(UUID id, UUID userId) {
    }
}
