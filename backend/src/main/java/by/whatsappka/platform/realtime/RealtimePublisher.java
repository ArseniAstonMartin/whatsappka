package by.whatsappka.platform.realtime;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Приватная доставка: адресат задаётся сервером, клиент его не выбирает. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RealtimePublisher {

    private final SimpMessagingTemplate template;
    private final UserAccountRepository users;

    public RealtimePublisher(SimpMessagingTemplate template, UserAccountRepository users) {
        this.template = template;
        this.users = users;
    }

    /** Возвращает false, если аккаунт неактивен или не существует: событие не отправляется. */
    @Transactional(readOnly = true)
    public boolean deliver(UUID userId, RealtimeEvent event) {
        if (users.findById(userId).filter(UserAccount::isActive).isEmpty()) {
            return false;
        }
        template.convertAndSendToUser(userId.toString(), "/queue/events", event);
        return true;
    }
}
