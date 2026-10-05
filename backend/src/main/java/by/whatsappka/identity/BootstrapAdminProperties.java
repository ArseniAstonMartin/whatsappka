package by.whatsappka.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Первый администратор из окружения. Пустой email означает, что создание не запрошено.
 * Пароля по умолчанию нет: без него запуск с заданным email завершится ошибкой.
 */
@ConfigurationProperties("whatsappka.bootstrap-admin")
public record BootstrapAdminProperties(String email, String username, String password) {

    public boolean requested() {
        return email != null && !email.isBlank();
    }
}
