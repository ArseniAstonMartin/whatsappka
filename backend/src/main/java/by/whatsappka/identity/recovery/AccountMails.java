package by.whatsappka.identity.recovery;

import by.whatsappka.identity.session.RefreshTokens;
import by.whatsappka.platform.jobs.JobQueue;
import by.whatsappka.platform.mail.MailDeliveryHandler;
import by.whatsappka.platform.mail.MailProperties;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Письма со ссылкой. Письмо ставится в очередь в той же транзакции, что и токен: без коммита письма нет.
 * Отправку выполняет worker, поэтому запрос не ждёт SMTP.
 */
@Service
public class AccountMails {

    private final JobQueue jobs;
    private final MailProperties mail;

    public AccountMails(JobQueue jobs, MailProperties mail) {
        this.jobs = jobs;
        this.mail = mail;
    }

    public void queueLink(String to, String subject, String intro, String path, String token, Duration lifetime) {
        String link = mail.appUrl() + path + "?token=" + token;
        String text = intro + "\n\n" + link + "\n\n"
                + "Ссылка действует " + lifetime.toHours() + " ч. Если вы не запрашивали это письмо, просто проигнорируйте его.";
        // Ключ зависит от хеша токена, а не от самого токена: повторная постановка того же письма не дублирует его.
        jobs.enqueue(MailDeliveryHandler.TYPE, "account.mail:" + RefreshTokens.hash(token),
                Map.of("to", to, "subject", subject, "text", text));
    }
}
