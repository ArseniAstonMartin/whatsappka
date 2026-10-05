package by.whatsappka.platform.outbox;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Relay выключен по умолчанию: события доставляет только процесс worker. */
@Validated
@ConfigurationProperties("whatsappka.outbox")
public record OutboxProperties(
        @DefaultValue("false") boolean relayEnabled,
        @DefaultValue("20") @Min(1) @Max(200) int batchSize,
        @DefaultValue("PT2M") Duration lease
) {
}
