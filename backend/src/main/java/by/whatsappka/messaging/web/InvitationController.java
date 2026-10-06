package by.whatsappka.messaging.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.messaging.InvitationService;
import by.whatsappka.platform.idempotency.IdempotencyRecords;
import by.whatsappka.platform.idempotency.IdempotentResponse;
import by.whatsappka.platform.idempotency.IdempotentResult;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.ApiV1Controller;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InvitationController {

    private static final String CREATE_OPERATION = "invitation.create";

    private final InvitationService invitations;
    private final IdempotencyRecords idempotency;

    public InvitationController(InvitationService invitations, IdempotencyRecords idempotency) {
        this.invitations = invitations;
        this.idempotency = idempotency;
    }

    /** Создание требует Idempotency-Key: повтор после разрыва связи возвращает тот же результат. */
    @PostMapping("/conversations/{id}/invitations")
    public ResponseEntity<JsonNode> invite(
            @PathVariable("id") UUID conversationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ChatInviteRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        if (key == null) {
            throw ApiException.badRequest("bad_request", "Заголовок Idempotency-Key обязателен");
        }
        byte[] content = IdempotencyRecords.utf8(conversationId + "|" + request.userId());
        IdempotentResult result = idempotency.execute(viewer.userId(), CREATE_OPERATION, key, content, () -> {
            UUID id = invitations.invite(viewer.userId(), conversationId, request.userId());
            return new IdempotentResponse(HttpStatus.CREATED.value(), new ChatInvitationRef(id));
        });
        return ResponseEntity.status(result.status())
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.body());
    }

    @PostMapping("/invitations/{id}/accept")
    public ResponseEntity<Void> accept(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        invitations.accept(viewer.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        invitations.decline(viewer.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        invitations.revoke(viewer.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/invitations")
    public List<InvitationService.PendingView> mine(@AuthenticationPrincipal AuthenticatedUser viewer) {
        return invitations.pendingFor(viewer.userId());
    }

    public record ChatInviteRequest(UUID userId) {
    }

    public record ChatInvitationRef(UUID id) {
    }
}
