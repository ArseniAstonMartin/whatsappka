package by.whatsappka.content.web;

import by.whatsappka.content.CommentService;
import by.whatsappka.content.CommentService.CommentView;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Вложенные комментарии к публикации (TASK-048). */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentRef> create(
            @PathVariable("postId") UUID postId,
            @RequestBody CreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        UUID id = comments.create(viewer.userId(), postId, request.parentId(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CommentRef(id));
    }

    @GetMapping("/posts/{postId}/comments")
    public CursorPage<CommentView> list(
            @PathVariable("postId") UUID postId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return comments.list(viewer.userId(), postId, cursor, limit);
    }

    @PatchMapping("/comments/{id}")
    public ResponseEntity<Void> update(
            @PathVariable("id") UUID id,
            @RequestBody UpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        comments.update(id, viewer.userId(), request.version(), request.body());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        comments.delete(id, viewer.userId());
        return ResponseEntity.noContent().build();
    }

    public record CreateRequest(String body, UUID parentId) {
    }

    public record UpdateRequest(String body, long version) {
    }

    public record CommentRef(UUID id) {
    }
}
