package by.whatsappka.communities.web;

import by.whatsappka.communities.CommunityMembershipService;
import by.whatsappka.communities.CommunityMembershipService.Member;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Вступление, выход, состав и роли сообщества (TASK-036). Создание и настройки — {@code CommunityController}. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommunityMembershipController {

    private final CommunityMembershipService membership;

    public CommunityMembershipController(CommunityMembershipService membership) {
        this.membership = membership;
    }

    @PostMapping("/groups/{id}/join")
    public ResponseEntity<Void> join(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        membership.join(id, viewer.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        membership.leave(id, viewer.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/groups/{id}/members")
    public List<Member> members(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return membership.members(viewer.userId(), id);
    }

    @PostMapping("/groups/{id}/members/{userId}/remove")
    public ResponseEntity<Void> remove(
            @PathVariable("id") UUID id,
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        membership.remove(id, viewer.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/groups/{id}/members/{userId}/role")
    public ResponseEntity<Void> setRole(
            @PathVariable("id") UUID id,
            @PathVariable("userId") UUID userId,
            @RequestBody CommunityRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        membership.setRole(id, viewer.userId(), userId, request.role());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/{id}/transfer")
    public ResponseEntity<Void> transfer(
            @PathVariable("id") UUID id,
            @RequestBody CommunityTransferRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        membership.transferOwnership(id, viewer.userId(), request.userId());
        return ResponseEntity.noContent().build();
    }

    public record CommunityRoleRequest(String role) {
    }

    public record CommunityTransferRequest(UUID userId) {
    }
}
