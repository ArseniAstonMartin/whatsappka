package by.whatsappka.messaging;

import by.whatsappka.platform.realtime.EphemeralState;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

/**
 * Сигнал набора текста. Не чаще раза в {@link #RATE} на пользователя и диалог; состояние живёт в Redis и в PostgreSQL
 * не пишется. Лишние и недопустимые сигналы молча отбрасываются: ответ не раскрывает, кто и где печатает.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TypingService {

    static final Duration RATE = Duration.ofSeconds(2);
    /** Срок жизни сигнала у получателя: по истечении клиент убирает индикатор, даже если сигнал об окончании не пришёл. */
    public static final int EXPIRES_IN_SECONDS = 5;

    private final ConversationGuard guard;
    private final MessageService messages;
    private final EphemeralState state;

    public TypingService(ConversationGuard guard, MessageService messages, EphemeralState state) {
        this.guard = guard;
        this.messages = messages;
        this.state = state;
    }

    /** Адресаты сигнала. Пусто, если отправитель не участник, между сторонами блокировка, частота превышена или Redis недоступен. */
    public List<UUID> signal(UUID sender, UUID conversationId) {
        if (!guard.isActiveMember(conversationId, sender) || guard.blockedInDirect(sender, conversationId)) {
            return List.of();
        }
        if (!state.claim(rateKey(conversationId, sender), RATE)) {
            return List.of();
        }
        return messages.recipients(conversationId, sender);
    }

    private static String rateKey(UUID conversationId, UUID sender) {
        return "typing:rate:" + conversationId + ":" + sender;
    }
}
