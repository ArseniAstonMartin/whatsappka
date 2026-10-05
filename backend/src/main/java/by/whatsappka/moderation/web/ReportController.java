package by.whatsappka.moderation.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.moderation.ReportService;
import by.whatsappka.platform.web.ApiV1Controller;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** Жалоба на доступный объект. Повторная открытая жалоба на тот же объект — 409. */
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportService.Submitted submit(
            @RequestBody SubmitRequest request,
            @AuthenticationPrincipal AuthenticatedUser reporter
    ) {
        return reports.submit(reporter.userId(), request.targetKind(), request.targetId(), request.reason(), request.description());
    }

    public record SubmitRequest(String targetKind, UUID targetId, String reason, String description) {
    }
}
