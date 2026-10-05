package by.whatsappka.sanctions.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.TraceIds;
import by.whatsappka.sanctions.SanctionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Временная блокировка модератором. Постоянная и снятие — только в /admin. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ModerationSanctionController {

    private final SanctionService sanctions;

    public ModerationSanctionController(SanctionService sanctions) {
        this.sanctions = sanctions;
    }

    @PostMapping("/moderation/users/{id}/sanctions")
    public SanctionService.Issued issue(
            @PathVariable("id") UUID userId,
            @RequestBody TemporaryRequest body,
            @AuthenticationPrincipal AuthenticatedUser moderator,
            HttpServletRequest request
    ) {
        return sanctions.issue(moderator.userId(), moderator.roles().contains("ADMIN"), userId, "TEMPORARY",
                body.durationHours(), body.reason(), TraceIds.current(request));
    }

    public record TemporaryRequest(Integer durationHours, String reason) {
    }
}
