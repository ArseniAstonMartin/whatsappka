package by.whatsappka.messaging.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.messaging.GroupQueries;
import by.whatsappka.messaging.GroupService;
import by.whatsappka.platform.web.ApiV1Controller;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GroupController {

    private final GroupService groups;
    private final GroupQueries queries;

    public GroupController(GroupService groups, GroupQueries queries) {
        this.groups = groups;
        this.queries = queries;
    }

    @PostMapping("/conversations/groups")
    public ResponseEntity<GroupRef> create(@RequestBody CreateGroupRequest request, @AuthenticationPrincipal AuthenticatedUser viewer) {
        UUID id = groups.create(viewer.userId(), request.title(), request.avatarMediaId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new GroupRef(id));
    }

    @GetMapping("/conversations/{id}/members")
    public List<GroupQueries.Member> members(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return queries.members(viewer.userId(), id);
    }

    @PostMapping("/conversations/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        groups.leave(id, viewer.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/members/{userId}/remove")
    public ResponseEntity<Void> remove(
            @PathVariable("id") UUID id,
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        groups.remove(id, viewer.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/conversations/{id}/members/{userId}/role")
    public ResponseEntity<Void> setRole(
            @PathVariable("id") UUID id,
            @PathVariable("userId") UUID userId,
            @RequestBody RoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        groups.setRole(id, viewer.userId(), userId, request.role());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/transfer")
    public ResponseEntity<Void> transfer(
            @PathVariable("id") UUID id,
            @RequestBody TransferRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        groups.transferOwnership(id, viewer.userId(), request.userId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/conversations/{id}/avatar")
    public ResponseEntity<Void> avatar(
            @PathVariable("id") UUID id,
            @RequestBody AvatarRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        groups.changeAvatar(id, viewer.userId(), request.mediaId());
        return ResponseEntity.noContent().build();
    }

    public record CreateGroupRequest(String title, UUID avatarMediaId) {
    }

    public record GroupRef(UUID id) {
    }

    public record RoleRequest(String role) {
    }

    public record TransferRequest(UUID userId) {
    }

    public record AvatarRequest(UUID mediaId) {
    }
}
