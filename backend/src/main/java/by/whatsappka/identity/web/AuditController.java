package by.whatsappka.identity.web;

import by.whatsappka.identity.audit.AuditQueries;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Журнал аудита, только чтение. Доступ по правилам /moderation/** (модератор и администратор), объём — по роли. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuditController {

    private final AuditQueries audit;

    public AuditController(AuditQueries audit) {
        this.audit = audit;
    }

    @GetMapping("/moderation/audit")
    public CursorPage<AuditQueries.Entry> list(
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "targetType", required = false) String targetType,
            @RequestParam(name = "actorId", required = false) UUID actorId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return audit.page(viewer, new AuditQueries.Filter(blankToNull(action), blankToNull(targetType), actorId), cursor, limit);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
