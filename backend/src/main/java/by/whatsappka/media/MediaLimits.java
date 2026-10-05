package by.whatsappka.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Квоты и пороги из FR-10. Значения по умолчанию соответствуют PRD; общий лимит и минимум свободного места
 * настраиваются окружением, но не могут быть отключены.
 */
@ConfigurationProperties("whatsappka.media-limits")
public record MediaLimits(
        @DefaultValue("268435456") long userQuotaBytes,
        @DefaultValue("10737418240") long globalLimitBytes,
        @DefaultValue("5368709120") long minFreeDiskBytes,
        @DefaultValue(".") String diskPath,
        @DefaultValue("PT2H") java.time.Duration reservationTtl
) {
}
