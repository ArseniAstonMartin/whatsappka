package by.whatsappka.moderation.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.moderation.EvidenceService;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Доказательство жалобы. Доступ только модераторам и администраторам (правило безопасности /moderation/**). */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ModerationEvidenceController {

    private final EvidenceService evidence;

    public ModerationEvidenceController(EvidenceService evidence) {
        this.evidence = evidence;
    }

    @GetMapping("/moderation/reports/{id}/evidence")
    public EvidenceService.EvidenceView view(
            @PathVariable("id") UUID reportId,
            @AuthenticationPrincipal AuthenticatedUser moderator,
            HttpServletRequest request
    ) {
        return evidence.view(moderator.userId(), reportId, TraceIds.current(request));
    }
}
