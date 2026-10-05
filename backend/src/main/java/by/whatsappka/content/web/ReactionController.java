package by.whatsappka.content.web;

import by.whatsappka.content.ReactionService;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Реакции на посты и комментарии (TASK-050). */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ReactionController {

    private final ReactionService reactions;

    public ReactionController(ReactionService reactions) {
        this.reactions = reactions;
    }

    @PutMapping("/posts/{id}/reaction")
    public ResponseEntity<Void> setPostReaction(
            @PathVariable("id") UUID id, @RequestBody ReactionRequest request, @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        reactions.setPostReaction(viewer.userId(), id, request.type());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{id}/reaction")
    public ResponseEntity<Void> removePostReaction(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        reactions.removePostReaction(viewer.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/comments/{id}/reaction")
    public ResponseEntity<Void> setCommentReaction(
            @PathVariable("id") UUID id, @RequestBody ReactionRequest request, @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        reactions.setCommentReaction(viewer.userId(), id, request.type());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/comments/{id}/reaction")
    public ResponseEntity<Void> removeCommentReaction(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        reactions.removeCommentReaction(viewer.userId(), id);
        return ResponseEntity.noContent().build();
    }

    public record ReactionRequest(String type) {
    }
}
