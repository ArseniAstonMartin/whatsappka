package by.whatsappka.platform.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Отправка писем через SMTP из настроек окружения. Вызывается только из заданий worker. */
@Component
public class MailSender {

    private final MailProperties properties;
    private final JavaMailSenderImpl transport;

    public MailSender(MailProperties properties) {
        this.properties = properties;
        this.transport = new JavaMailSenderImpl();
        properties.configuredHost().ifPresent(transport::setHost);
        transport.setPort(properties.port());
        if (!properties.username().isBlank()) {
            transport.setUsername(properties.username());
            transport.setPassword(properties.password());
        }
        Properties javaMail = transport.getJavaMailProperties();
        javaMail.put("mail.smtp.starttls.enable", String.valueOf(properties.startTls()));
        javaMail.put("mail.smtp.connectiontimeout", "5000");
        javaMail.put("mail.smtp.timeout", "10000");
        javaMail.put("mail.smtp.writetimeout", "10000");
    }

    /**
     * Текст письма содержит одноразовую ссылку, поэтому журнал отправки не должен печатать тело.
     * Без настроенного SMTP отправка падает с понятной причиной, и задание повторяется по политике очереди.
     */
    public void send(String to, String subject, String text) {
        if (properties.configuredHost().isEmpty()) {
            throw new IllegalStateException("SMTP не настроен: задайте SMTP_HOST");
        }
        try {
            MimeMessage message = transport.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            transport.send(message);
        } catch (MessagingException | MailException e) {
            throw new IllegalStateException("Письмо не отправлено: " + e.getClass().getSimpleName(), e);
        }
    }
}
