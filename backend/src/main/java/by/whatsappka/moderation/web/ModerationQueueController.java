package by.whatsappka.moderation.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.moderation.ModerationService;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Очередь жалоб и решения. Доступ модераторам и администраторам по правилу /moderation/**. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ModerationQueueController {

    private final ModerationService moderation;

    public ModerationQueueController(ModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping("/moderation/reports")
    public CursorPage<ModerationService.QueueItem> queue(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return moderation.queue(status, cursor, limit);
    }

    @PostMapping("/moderation/reports/{id}/take")
    public ModerationService.Outcome take(
            @PathVariable("id") UUID reportId,
            @AuthenticationPrincipal AuthenticatedUser moderator,
            HttpServletRequest request
    ) {
        return moderation.take(moderator.userId(), reportId, TraceIds.current(request));
    }

    @PostMapping("/moderation/reports/{id}/resolve")
    public ModerationService.Outcome resolve(
            @PathVariable("id") UUID reportId,
            @RequestBody DecisionRequest body,
            @AuthenticationPrincipal AuthenticatedUser moderator,
            HttpServletRequest request
    ) {
        return moderation.resolve(moderator.userId(), reportId, body.action(), body.reason(), TraceIds.current(request));
    }

    @PostMapping("/moderation/reports/{id}/reject")
    public ModerationService.Outcome reject(
            @PathVariable("id") UUID reportId,
            @RequestBody ReasonRequest body,
            @AuthenticationPrincipal AuthenticatedUser moderator,
            HttpServletRequest request
    ) {
        return moderation.reject(moderator.userId(), reportId, body.reason(), TraceIds.current(request));
    }

    public record DecisionRequest(String action, String reason) {
    }

    public record ReasonRequest(String reason) {
    }
}
