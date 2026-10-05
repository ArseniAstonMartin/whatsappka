package by.whatsappka.content.web;

import by.whatsappka.content.FeedService;
import by.whatsappka.content.PostSummaryPublic;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Лента подписок, посты профиля и посты группы (TASK-045). */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FeedController {

    private final FeedService feed;

    public FeedController(FeedService feed) {
        this.feed = feed;
    }

    @GetMapping("/feed")
    public CursorPage<PostSummaryPublic> feed(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return feed.feed(viewer.userId(), cursor, limit);
    }

    @GetMapping("/users/{authorId}/posts")
    public CursorPage<PostSummaryPublic> profilePosts(
            @PathVariable("authorId") UUID authorId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return feed.profilePosts(viewer.userId(), authorId, cursor, limit);
    }

    @GetMapping("/groups/{id}/posts")
    public CursorPage<PostSummaryPublic> groupPosts(
            @PathVariable("id") UUID groupId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return feed.groupPosts(viewer.userId(), groupId, cursor, limit);
    }
}
