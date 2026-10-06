package by.whatsappka.settings;

import by.whatsappka.identity.audit.AuditService;
import by.whatsappka.media.MediaLimits;
import by.whatsappka.media.MediaPurpose;
import by.whatsappka.platform.web.ApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Настройки приложения. Значения по умолчанию безопасны: регистрация открыта, лимиты равны техническим пределам.
 * Изменение записывается в аудит с причиной. Закрытая регистрация не трогает вход существующих пользователей.
 */
@Service
public class AppSettingsService {

    static final String SITE_NAME_DEFAULT = "WhatsAppka";

    private static final String ALL = "SELECT key, value FROM app_settings";
    private static final String UPSERT = """
            INSERT INTO app_settings (key, value, updated_at, updated_by) VALUES (?, ?, now(), ?)
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at,
                                            updated_by = EXCLUDED.updated_by
            """;

    /** Технический потолок загрузки: самый большой из допустимых размеров назначения. */
    static long uploadCeiling() {
        return java.util.Arrays.stream(MediaPurpose.values()).mapToLong(MediaPurpose::maxBytes).max().orElse(AppSettingsRules.MIB);
    }

    public record Values(boolean registrationOpen, long uploadLimitBytes, long userQuotaBytes, String siteName) {
    }

    public record SettingsView(Values values, long uploadCeilingBytes, long quotaCeilingBytes) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final MediaLimits mediaLimits;

    public AppSettingsService(JdbcTemplate jdbc, AuditService audit, MediaLimits mediaLimits) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.mediaLimits = mediaLimits;
    }

    @Transactional(readOnly = true)
    public Values current() {
        Map<String, String> stored = new HashMap<>();
        jdbc.query(ALL, rs -> {
            stored.put(rs.getString("key"), rs.getString("value"));
        });
        return new Values(
                Boolean.parseBoolean(stored.getOrDefault(AppSettingKey.REGISTRATION_OPEN.column(), "true")),
                parseLong(stored.get(AppSettingKey.UPLOAD_LIMIT_BYTES.column()), uploadCeiling()),
                parseLong(stored.get(AppSettingKey.USER_QUOTA_BYTES.column()), mediaLimits.userQuotaBytes()),
                stored.getOrDefault(AppSettingKey.SITE_NAME.column(), SITE_NAME_DEFAULT));
    }

    @Transactional(readOnly = true)
    public SettingsView view() {
        return new SettingsView(current(), uploadCeiling(), mediaLimits.userQuotaBytes());
    }

    /** Закрытая регистрация: новые аккаунты не создаются ни паролем, ни через Google. */
    public void requireRegistrationOpen() {
        if (!current().registrationOpen()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "registration_closed",
                    "Регистрация новых аккаунтов сейчас закрыта", List.of(), null);
        }
    }

    /** Лимит одной загрузки: настройка, но не выше технического потолка назначения. */
    public long effectiveUploadLimit(MediaPurpose purpose) {
        return Math.min(current().uploadLimitBytes(), purpose.maxBytes());
    }

    /** Квота пользователя: настройка, но не выше технической квоты стенда. */
    public long effectiveUserQuota() {
        return Math.min(current().userQuotaBytes(), mediaLimits.userQuotaBytes());
    }

    @Transactional
    public Values update(UUID actorId, AppSettingKey key, String rawValue, String rawReason, String traceId) {
        String reason = AppSettingsRules.normalizeReason(rawReason);
        String value = AppSettingsRules.normalize(key, rawValue, uploadCeiling(), mediaLimits.userQuotaBytes());
        jdbc.update(UPSERT, key.column(), value, actorId);
        audit.record(actorId, "APP_SETTING_CHANGED", "app_setting", null,
                key.column() + "=" + value + ": " + reason, traceId);
        return current();
    }

    private static long parseLong(String stored, long fallback) {
        if (stored == null) {
            return fallback;
        }
        return Long.parseLong(stored);
    }
}
