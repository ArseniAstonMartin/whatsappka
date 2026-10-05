package by.whatsappka.social.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.social.FollowQueries;
import by.whatsappka.social.FollowService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SocialController {

    private final FollowService follows;
    private final FollowQueries queries;

    public SocialController(FollowService follows, FollowQueries queries) {
        this.follows = follows;
        this.queries = queries;
    }

    @PutMapping("/users/{id}/follow")
    public ResponseEntity<Void> follow(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        follows.follow(viewer.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/follow")
    public ResponseEntity<Void> unfollow(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        follows.unfollow(viewer.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{id}/followers")
    public CursorPage<FollowQueries.UserSummary> followers(
            @PathVariable("id") UUID id,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return queries.followers(id, cursor, limitOrDefault(limit));
    }

    @GetMapping("/users/{id}/following")
    public CursorPage<FollowQueries.UserSummary> following(
            @PathVariable("id") UUID id,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return queries.following(id, cursor, limitOrDefault(limit));
    }

    @GetMapping("/users/{id}/relations")
    public FollowQueries.Relations relations(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return queries.relations(viewer.userId(), id);
    }

    private static int limitOrDefault(Integer limit) {
        return limit == null ? by.whatsappka.platform.web.PageSize.DEFAULT : limit;
    }
}
