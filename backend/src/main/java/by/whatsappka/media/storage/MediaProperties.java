package by.whatsappka.media.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки закрытого bucket MinIO. Секретный ключ задаётся только через окружение. */
@ConfigurationProperties("whatsappka.media")
public record MediaProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey
) {
}
