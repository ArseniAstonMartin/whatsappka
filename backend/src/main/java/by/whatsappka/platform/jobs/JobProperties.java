package by.whatsappka.platform.jobs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Worker выключен по умолчанию: процесс API не обрабатывает задания, это делает отдельный worker. */
@Validated
@ConfigurationProperties("whatsappka.jobs")
public record JobProperties(
        @DefaultValue("false") boolean workerEnabled,
        @DefaultValue("2") @Min(1) @Max(8) int concurrency,
        @DefaultValue("PT5M") Duration lease
) {
}
