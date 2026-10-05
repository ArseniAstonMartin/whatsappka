package by.whatsappka.social;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.platform.web.ApiException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/**
 * Общие проверки связей между пользователями. Личные блокировки (TASK-031) добавятся сюда,
 * а не в контроллеры и не в отдельные сервисы.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SocialRelations {

    private final UserAccountRepository users;

    public SocialRelations(UserAccountRepository users) {
        this.users = users;
    }

    /** Подписаться можно только на другого активного пользователя. */
    public void requireCanFollow(UUID viewerId, UUID targetId) {
        if (viewerId.equals(targetId)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "self_follow",
                    "Нельзя подписаться на себя", java.util.List.of(), null);
        }
        requireActiveTarget(targetId);
    }

    /** Чтение связей доступно только для активного пользователя. */
    public UserAccount requireActiveTarget(UUID targetId) {
        return users.findById(targetId).filter(UserAccount::isActive).orElseThrow(ApiException::notFound);
    }
}
