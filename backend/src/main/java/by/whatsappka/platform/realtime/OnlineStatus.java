package by.whatsappka.platform.realtime;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/**
 * Присутствие: пользователь онлайн, пока у него есть живое подтверждённое соединение. Сервер продлевает отметку
 * при обходе соединений; без продления она истекает через {@link #TTL}. В PostgreSQL присутствие не пишется.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OnlineStatus {

    static final Duration TTL = Duration.ofSeconds(30);

    private final EphemeralState state;

    public OnlineStatus(EphemeralState state) {
        this.state = state;
    }

    public void touch(UUID userId) {
        state.mark(key(userId), TTL);
    }

    public void clear(UUID userId) {
        state.remove(key(userId));
    }

    public boolean isOnline(UUID userId) {
        return state.exists(key(userId));
    }

    static String key(UUID userId) {
        return "presence:user:" + userId;
    }
}
