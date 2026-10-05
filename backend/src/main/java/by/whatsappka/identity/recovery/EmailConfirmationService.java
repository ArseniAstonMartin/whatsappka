package by.whatsappka.identity.recovery;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Подтверждение email. Регистрация ставит письмо в очередь; подтверждённый адрес письма не получает. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class EmailConfirmationService {

    static final Duration LIFETIME = Duration.ofHours(48);

    private final UserAccountRepository users;
    private final AccountTokens tokens;
    private final AccountMails mails;
    private final Clock clock;

    public EmailConfirmationService(UserAccountRepository users, AccountTokens tokens, AccountMails mails, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.mails = mails;
        this.clock = clock;
    }

    /** Ставит письмо с подтверждением, если адрес ещё не подтверждён. Вызывается внутри транзакции создания аккаунта. */
    @Transactional
    public void send(UserAccount user) {
        if (user.isEmailVerified()) {
            return;
        }
        String token = tokens.issue(user.id(), AccountTokens.EMAIL_CONFIRMATION, LIFETIME);
        mails.queueLink(user.email(), "Подтверждение почты в WhatsAppka",
                "Чтобы подтвердить адрес почты, откройте ссылку:",
                "/confirm-email", token, LIFETIME);
    }

    /** Повторная отправка по запросу вошедшего пользователя. */
    @Transactional
    public void resend(UUID userId) {
        users.findById(userId).filter(UserAccount::isActive).ifPresent(this::send);
    }

    @Transactional
    public void confirm(String token) {
        UUID userId = tokens.consume(AccountTokens.EMAIL_CONFIRMATION, token).orElseThrow(AccountTokens::invalidToken);
        UserAccount user = users.findById(userId).filter(UserAccount::isActive).orElseThrow(AccountTokens::invalidToken);
        user.markEmailVerified(clock.instant());
    }
}
