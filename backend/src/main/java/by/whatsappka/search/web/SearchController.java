package by.whatsappka.search.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.search.SearchQueries;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SearchController {

    private final SearchQueries search;

    public SearchController(SearchQueries search) {
        this.search = search;
    }

    @GetMapping("/search/users")
    public CursorPage<SearchQueries.UserHit> users(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return search.users(viewer.userId(), q, cursor, limit);
    }

    @GetMapping("/search/groups")
    public CursorPage<SearchQueries.GroupHit> groups(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return search.groups(q, cursor, limit);
    }
}
