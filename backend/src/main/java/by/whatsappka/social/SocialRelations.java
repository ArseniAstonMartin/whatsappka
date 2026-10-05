package by.whatsappka.social;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.platform.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Единая точка правил личного взаимодействия (FR-08). Профиль, подписки, списки и будущие чаты, приглашения
 * и личный контент обязаны проверять доступ здесь, а не в контроллерах.
 *
 * Блокировка скрывает объект от обеих сторон: ответ 404 не выдаёт, что аккаунт существует.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SocialRelations {

    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;

    public SocialRelations(UserAccountRepository users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    /** Подписаться можно только на другого активного пользователя, не находясь с ним в блокировке. */
    public void requireCanFollow(UUID viewerId, UUID targetId) {
        if (viewerId.equals(targetId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "self_follow", "Нельзя подписаться на себя",
                    List.of(), null);
        }
        requireVisibleTo(viewerId, targetId);
    }

    /** Личный объект другого пользователя: нужен активный аккаунт и отсутствие блокировки в любую сторону. */
    public void requireVisibleTo(UUID viewerId, UUID targetId) {
        requireActiveTarget(targetId);
        if (!viewerId.equals(targetId) && blockedEitherWay(viewerId, targetId)) {
            throw ApiException.notFound();
        }
    }

    /** Чтение данных активного аккаунта. */
    public UserAccount requireActiveTarget(UUID targetId) {
        return users.findById(targetId).filter(UserAccount::isActive).orElseThrow(ApiException::notFound);
    }

    /** true, если любой из двух аккаунтов заблокировал другого. */
    protected boolean blockedEitherWay(UUID a, UUID b) {
        Boolean blocked = jdbc.queryForObject(SocialSql.IS_BLOCKED_EITHER_WAY, Boolean.class, a, b, b, a);
        return Boolean.TRUE.equals(blocked);
    }
}
