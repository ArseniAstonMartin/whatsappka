package by.whatsappka.content.web;

import by.whatsappka.content.PostSchedulingService;
import by.whatsappka.content.PostService;
import by.whatsappka.content.PostService.PostSummary;
import by.whatsappka.content.PostService.PostView;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.idempotency.IdempotencyRecords;
import by.whatsappka.platform.idempotency.IdempotentResponse;
import by.whatsappka.platform.idempotency.IdempotentResult;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** CRUD публикаций (TASK-041), немедленная публикация (TASK-042) и расписание (TASK-043). */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PostController {

    private static final String PUBLISH_OPERATION = "post.publish";

    private final PostService posts;
    private final PostSchedulingService scheduling;
    private final IdempotencyRecords idempotency;

    public PostController(PostService posts, PostSchedulingService scheduling, IdempotencyRecords idempotency) {
        this.posts = posts;
        this.scheduling = scheduling;
        this.idempotency = idempotency;
    }

    @PostMapping("/posts")
    public ResponseEntity<PostRef> create(@RequestBody CreateRequest request, @AuthenticationPrincipal AuthenticatedUser viewer) {
        UUID id = posts.create(viewer.userId(), request.groupId(), request.body(), request.media(), request.hashtags());
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
        posts.update(id, viewer.userId(), request.version(), request.body(), request.media(), request.hashtags());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        posts.delete(id, viewer.userId());
        return ResponseEntity.noContent().build();
    }

    /** Idempotency-Key защищает от двойной публикации при повторе запроса после разрыва связи. */
    @PostMapping("/posts/{id}/publish")
    public ResponseEntity<JsonNode> publish(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        if (key == null) {
            throw ApiException.badRequest("bad_request", "Заголовок Idempotency-Key обязателен");
        }
        IdempotentResult result = idempotency.execute(viewer.userId(), PUBLISH_OPERATION, key,
                IdempotencyRecords.utf8(id.toString()), () -> {
                    posts.publish(id, viewer.userId());
                    return new IdempotentResponse(HttpStatus.OK.value(), new PostRef(id));
                });
        return ResponseEntity.status(result.status())
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.body());
    }

    @PostMapping("/posts/{id}/schedule")
    public ResponseEntity<Void> schedule(
            @PathVariable("id") UUID id,
            @RequestBody ScheduleRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        scheduling.schedule(id, viewer.userId(), request.publishAt());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/schedule/cancel")
    public ResponseEntity<Void> cancelSchedule(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        scheduling.cancelSchedule(id, viewer.userId());
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

    public record CreateRequest(String body, UUID groupId, List<UUID> media, List<String> hashtags) {
    }

    public record UpdateRequest(String body, List<UUID> media, List<String> hashtags, long version) {
    }

    public record ScheduleRequest(Instant publishAt) {
    }

    public record PostRef(UUID id) {
    }
}
