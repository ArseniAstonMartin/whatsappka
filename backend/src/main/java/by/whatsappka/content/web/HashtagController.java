package by.whatsappka.content.web;

import by.whatsappka.content.HashtagService;
import by.whatsappka.content.HashtagService.PostSummaryPublic;
import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Список опубликованных постов по хештегу (TASK-044). */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HashtagController {

    private final HashtagService hashtags;

    public HashtagController(HashtagService hashtags) {
        this.hashtags = hashtags;
    }

    @GetMapping("/hashtags/{tag}/posts")
    public CursorPage<PostSummaryPublic> posts(
            @PathVariable("tag") String tag,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return hashtags.posts(viewer.userId(), tag, cursor, limit);
    }
}
