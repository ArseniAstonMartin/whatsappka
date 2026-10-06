package by.whatsappka.admin;

import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.TraceIds;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.settings.AppSettingKey;
import by.whatsappka.settings.AppSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Настройки приложения для администратора. Всё под /admin/** — только ADMIN по правилу безопасности. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminSettingsController {

    private final AppSettingsService settings;

    public AdminSettingsController(AppSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/admin/settings")
    public AppSettingsService.SettingsView view() {
        return settings.view();
    }

    @PutMapping("/admin/settings/{key}")
    public AppSettingsService.Values update(
            @PathVariable("key") String key,
            @RequestBody UpdateRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest request
    ) {
        AppSettingKey parsed = AppSettingKey.require(key);
        String value = body.value() == null || body.value().isNull() ? null : body.value().asText();
        return settings.update(admin.userId(), parsed, value, body.reason(), TraceIds.current(request));
    }

    /** value — строка, логическое или число; сервер приводит к каноническому виду по правилам настройки. */
    public record UpdateRequest(JsonNode value, String reason) {
    }
}
