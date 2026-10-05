package by.whatsappka.content.web;

import by.whatsappka.content.PostService;
import by.whatsappka.content.PostService.PostSummary;
import by.whatsappka.content.PostService.PostView;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import java.util.List;
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

/** CRUD черновиков публикаций (TASK-041). Немедленная публикация и расписание — отдельные задачи. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PostController {

    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    @PostMapping("/posts")
    public ResponseEntity<PostRef> create(@RequestBody CreateRequest request, @AuthenticationPrincipal AuthenticatedUser viewer) {
        UUID id = posts.create(viewer.userId(), request.groupId(), request.body(), request.media());
        return ResponseEntity.status(HttpStatus.CREATED).body(new PostRef(id));
    }

    @GetMapping("/posts/{id}")
    public PostView get(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return posts.get(id, viewer.userId());
    }

    @PatchMapping("/posts/{id}")
    public ResponseEntity<Void> update(
            @PathVariable("id") UUID id,
            @RequestBody UpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        posts.update(id, viewer.userId(), request.version(), request.body(), request.media());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        posts.delete(id, viewer.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/post-drafts")
    public CursorPage<PostSummary> drafts(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return posts.drafts(viewer.userId(), cursor, limit);
    }

    public record CreateRequest(String body, UUID groupId, List<UUID> media) {
    }

    public record UpdateRequest(String body, List<UUID> media, long version) {
    }

    public record PostRef(UUID id) {
    }
}
