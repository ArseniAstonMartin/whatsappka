package by.whatsappka.identity.security;

import by.whatsappka.identity.AuthenticatedSession;
import by.whatsappka.identity.SessionService;
import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.identity.account.UserRoleRepository;
import by.whatsappka.platform.web.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Проверяет Bearer-токен и собирает принципала с актуальными ролями из БД. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccessService {

    private final SessionService sessions;
    private final UserAccountRepository users;
    private final UserRoleRepository roles;

    public AccessService(SessionService sessions, UserAccountRepository users, UserRoleRepository roles) {
        this.sessions = sessions;
        this.users = users;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(String authorization) {
        AuthenticatedSession session = sessions.authenticate(authorization);
        UserAccount user = users.findById(session.userId())
                .filter(UserAccount::isActive)
                .orElseThrow(ApiException::unauthorized);
        return new AuthenticatedUser(
                user.id(),
                session.sessionId(),
                user.username(),
                roles.findRoleCodes(user.id())
        );
    }
}
