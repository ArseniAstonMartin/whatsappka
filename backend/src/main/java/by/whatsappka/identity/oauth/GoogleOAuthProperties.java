package by.whatsappka.identity.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки Google OAuth. Секрет клиента живёт только на сервере.
 * Без client-id и client-secret вход через Google не работает и явно сообщает об этом.
 */
@ConfigurationProperties("whatsappka.oauth.google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String appUrl
) {

    public boolean configured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri) && notBlank(appUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
