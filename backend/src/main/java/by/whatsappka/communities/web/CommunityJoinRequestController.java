package by.whatsappka.communities.web;

import by.whatsappka.communities.CommunityJoinRequestService;
import by.whatsappka.communities.CommunityJoinRequestService.Request;
import by.whatsappka.identity.security.AuthenticatedUser;
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

/** Заявки на вступление в приватное сообщество (TASK-037). */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommunityJoinRequestController {

    private final CommunityJoinRequestService requests;

    public CommunityJoinRequestController(CommunityJoinRequestService requests) {
        this.requests = requests;
    }

    @PostMapping("/groups/{id}/join-requests")
    public ResponseEntity<RequestRef> create(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        UUID requestId = requests.request(id, viewer.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RequestRef(requestId));
    }

    @PostMapping("/groups/{id}/join-requests/{requestId}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable("id") UUID id,
            @PathVariable("requestId") UUID requestId,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        requests.cancel(id, viewer.userId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/{id}/join-requests/{requestId}/accept")
    public ResponseEntity<Void> accept(
            @PathVariable("id") UUID id,
            @PathVariable("requestId") UUID requestId,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        requests.accept(id, viewer.userId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/{id}/join-requests/{requestId}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable("id") UUID id,
            @PathVariable("requestId") UUID requestId,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        requests.reject(id, viewer.userId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/groups/{id}/join-requests")
    public List<Request> pending(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return requests.pending(id, viewer.userId());
    }

    @GetMapping("/groups/{id}/join-requests/mine")
    public ResponseEntity<RequestRef> mine(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        UUID requestId = requests.myPending(id, viewer.userId());
        return requestId == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(new RequestRef(requestId));
    }

    public record RequestRef(UUID id) {
    }
}
