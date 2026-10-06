package by.whatsappka.identity.activity;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Учёт активности для DAU. Записывается только сам авторизованный запрос: просмотр чужого профиля не
 * создаёт активности владельца профиля. В памяти держится набор пользователей за текущий UTC-день,
 * поэтому обычный запрос пишет в базу один раз за день.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ActivityRecorder {

    static final String RECORD = "INSERT INTO user_activity_days (user_id, day) VALUES (?, ?) ON CONFLICT DO NOTHING";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Map<UUID, LocalDate> recordedToday = new ConcurrentHashMap<>();
    private volatile LocalDate currentDay;

    public ActivityRecorder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void recordAuthenticated(UUID userId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        rollOverIfNeeded(today);
        if (recordedToday.containsKey(userId)) {
            return;
        }
        jdbc.update(RECORD, userId, Date.valueOf(today));
        recordedToday.put(userId, today);
    }

    private synchronized void rollOverIfNeeded(LocalDate today) {
        if (!today.equals(currentDay)) {
            recordedToday.clear();
            currentDay = today;
        }
    }
}
