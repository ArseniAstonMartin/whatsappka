package by.whatsappka.platform.mail;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Почта через SMTP. Пустой host означает, что доставка не настроена: задания почты завершаются понятной ошибкой. */
@Validated
@ConfigurationProperties("whatsappka.mail")
public record MailProperties(
        @DefaultValue("") String host,
        @DefaultValue("587") @Min(1) int port,
        @DefaultValue("") String username,
        @DefaultValue("") String password,
        @DefaultValue("true") boolean startTls,
        @NotBlank String from,
        @NotBlank String appUrl
) {

    public Optional<String> configuredHost() {
        return host == null || host.isBlank() ? Optional.empty() : Optional.of(host.trim());
    }
}
