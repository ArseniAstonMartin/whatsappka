package by.whatsappka.platform.mail;

import by.whatsappka.platform.jobs.JobContext;
import by.whatsappka.platform.jobs.JobHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Обработчик задания отправки письма. В полезной нагрузке лежит одноразовая ссылка, поэтому после успешной
 * отправки тело стирается: в таблице заданий остаётся только признак отправки.
 */
@Component
public class MailDeliveryHandler implements JobHandler {

    public static final String TYPE = "account.mail";

    private static final String ERASE_PAYLOAD = """
            UPDATE background_jobs SET payload = CAST('{"sent": true}' AS jsonb), updated_at = now() WHERE id = ?
            """;

    private final MailSender sender;
    private final JdbcTemplate jdbc;

    public MailDeliveryHandler(MailSender sender, JdbcTemplate jdbc) {
        this.sender = sender;
        this.jdbc = jdbc;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(JobContext context) {
        sender.send(
                context.payload().get("to").asText(),
                context.payload().get("subject").asText(),
                context.payload().get("text").asText()
        );
        jdbc.update(ERASE_PAYLOAD, context.jobId());
    }
}
