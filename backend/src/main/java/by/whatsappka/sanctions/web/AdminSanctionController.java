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

/** Санкции администратора: постоянная или временная блокировка, снятие. Правило безопасности /admin/** — только ADMIN. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminSanctionController {

    private final SanctionService sanctions;

    public AdminSanctionController(SanctionService sanctions) {
        this.sanctions = sanctions;
    }

    @PostMapping("/admin/users/{id}/sanctions")
    public SanctionService.Issued issue(
            @PathVariable("id") UUID userId,
            @RequestBody IssueRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest request
    ) {
        return sanctions.issue(admin.userId(), true, userId, body.kind(), body.durationHours(), body.reason(),
                TraceIds.current(request));
    }

    @PostMapping("/admin/sanctions/{id}/lift")
    public void lift(
            @PathVariable("id") UUID sanctionId,
            @RequestBody LiftRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest request
    ) {
        sanctions.lift(admin.userId(), sanctionId, body.reason(), TraceIds.current(request));
    }

    public record IssueRequest(String kind, Integer durationHours, String reason) {
    }

    public record LiftRequest(String reason) {
    }
}
