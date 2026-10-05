package by.whatsappka.moderation.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.moderation.AuthorNoticeService;
import by.whatsappka.platform.web.ApiV1Controller;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthorNoticeController {

    private final AuthorNoticeService notices;

    public AuthorNoticeController(AuthorNoticeService notices) {
        this.notices = notices;
    }

    /** Только автор скрытой публикации; для всех остальных — 404. */
    @GetMapping("/posts/{id}/moderation-notice")
    public AuthorNoticeService.HiddenPostNotice notice(
            @PathVariable("id") UUID postId,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return notices.hiddenPost(viewer.userId(), postId);
    }
}
