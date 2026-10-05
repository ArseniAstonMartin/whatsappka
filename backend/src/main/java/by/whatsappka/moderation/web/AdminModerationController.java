package by.whatsappka.moderation.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.moderation.ModerationService;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Восстановление скрытого — только администратор (правило /admin/**). */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminModerationController {

    private final ModerationService moderation;

    public AdminModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    @PostMapping("/admin/moderation/reports/{id}/restore")
    public ModerationService.Outcome restore(
            @PathVariable("id") UUID reportId,
            @RequestBody RestoreRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest request
    ) {
        return moderation.restore(admin.userId(), reportId, body.reason(), TraceIds.current(request));
    }

    public record RestoreRequest(String reason) {
    }
}
