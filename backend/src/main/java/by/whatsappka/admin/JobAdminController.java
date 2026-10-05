package by.whatsappka.admin;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** Доступ только ADMIN: правило {@code /api/v1/admin/**} задано в конфигурации безопасности на сервере. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JobAdminController {

    private final JobAdministration jobs;

    public JobAdminController(JobAdministration jobs) {
        this.jobs = jobs;
    }

    @PostMapping("/admin/jobs/{id}/retry")
    public ResponseEntity<Void> retry(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest request
    ) {
        jobs.retryFailed(actor.userId(), id, TraceIds.current(request));
        return ResponseEntity.noContent().build();
    }
}
