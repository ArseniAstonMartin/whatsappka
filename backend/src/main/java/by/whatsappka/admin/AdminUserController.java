package by.whatsappka.admin;

import by.whatsappka.admin.users.AdminUserQueries;
import by.whatsappka.admin.users.AdminUserService;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Управление ролями и верификацией. Всё под /admin/** — только ADMIN по правилу безопасности. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminUserController {

    private final AdminUserService users;
    private final AdminUserQueries queries;

    public AdminUserController(AdminUserService users, AdminUserQueries queries) {
        this.users = users;
        this.queries = queries;
    }

    @GetMapping("/admin/users")
    public CursorPage<AdminUserQueries.UserRow> list(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return queries.list(q, cursor, limit);
    }

    @PutMapping("/admin/users/{id}/roles")
    public AdminUserService.RolesResult roles(
            @PathVariable("id") UUID userId,
            @RequestBody RolesRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest request
    ) {
        return users.setRoles(admin.userId(), userId, body.roles(), body.reason(), TraceIds.current(request));
    }

    @PutMapping("/admin/users/{id}/verified")
    public AdminUserService.VerifiedResult verified(
            @PathVariable("id") UUID userId,
            @RequestBody VerifiedRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest request
    ) {
        return users.setVerified(admin.userId(), userId, body.verified(), body.reason(), TraceIds.current(request));
    }

    public record RolesRequest(List<String> roles, String reason) {
    }

    public record VerifiedRequest(boolean verified, String reason) {
    }
}
