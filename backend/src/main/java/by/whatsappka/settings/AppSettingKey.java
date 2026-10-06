package by.whatsappka.settings;

import by.whatsappka.platform.web.ApiException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/** Ключи настроек. Совпадают со списком в миграции V34: неизвестный ключ не попадает в таблицу. */
public enum AppSettingKey {
    REGISTRATION_OPEN("registration_open"),
    UPLOAD_LIMIT_BYTES("media_upload_limit_bytes"),
    USER_QUOTA_BYTES("media_user_quota_bytes"),
    SITE_NAME("site_name");

    private final String column;

    AppSettingKey(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }

    public static Optional<AppSettingKey> find(String name) {
        return Arrays.stream(values()).filter((key) -> key.column.equals(name)).findFirst();
    }

    /** Неизвестный ключ — 404: список допустимых настроек не раскрывается. */
    public static AppSettingKey require(String name) {
        return find(name).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Настройка не найдена", java.util.List.of(), null));
    }
}
