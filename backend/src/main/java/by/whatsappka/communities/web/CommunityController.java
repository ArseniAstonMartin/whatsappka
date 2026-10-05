package by.whatsappka.communities.web;

import by.whatsappka.communities.CommunityQueries;
import by.whatsappka.communities.CommunityQueries.CommunitySummary;
import by.whatsappka.communities.CommunityQueries.CommunityView;
import by.whatsappka.communities.CommunityService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Создание, каталог и настройки сообществ (FR-07). Состав и роли участников — отдельная задача. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommunityController {

    private final CommunityService groups;
    private final CommunityQueries queries;

    public CommunityController(CommunityService groups, CommunityQueries queries) {
        this.groups = groups;
        this.queries = queries;
    }

    @PostMapping("/groups")
    public ResponseEntity<GroupRef> create(@RequestBody CreateRequest request, @AuthenticationPrincipal AuthenticatedUser viewer) {
        UUID id = groups.create(viewer.userId(), request.slug(), request.name(), request.description(), request.visibility());
        return ResponseEntity.status(HttpStatus.CREATED).body(new GroupRef(id));
    }

    @GetMapping("/groups")
    public CursorPage<CommunitySummary> catalog(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return queries.catalog(cursor, limit);
    }

    @GetMapping("/me/groups")
    public List<CommunitySummary> myGroups(@AuthenticationPrincipal AuthenticatedUser viewer) {
        return queries.myGroups(viewer.userId());
    }

    @GetMapping("/groups/{id}")
    public CommunityView byId(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return queries.detailById(viewer.userId(), id);
    }

    @GetMapping("/groups/slug/{slug}")
    public CommunityView bySlug(@PathVariable("slug") String slug, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return queries.detailBySlug(viewer.userId(), slug);
    }

    @PatchMapping("/groups/{id}")
    public ResponseEntity<Void> update(
            @PathVariable("id") UUID id,
            @RequestBody UpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        groups.update(id, viewer.userId(), request.name(), request.description(), request.visibility());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        groups.delete(id, viewer.userId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/groups/{id}/avatar")
    public ResponseEntity<Void> avatar(
            @PathVariable("id") UUID id,
            @RequestBody MediaRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        groups.changeAvatar(id, viewer.userId(), request.mediaId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/groups/{id}/cover")
    public ResponseEntity<Void> cover(
            @PathVariable("id") UUID id,
            @RequestBody MediaRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        groups.changeCover(id, viewer.userId(), request.mediaId());
        return ResponseEntity.noContent().build();
    }

    public record CreateRequest(String slug, String name, String description, String visibility) {
    }

    public record UpdateRequest(String name, String description, String visibility) {
    }

    public record MediaRequest(UUID mediaId) {
    }

    public record GroupRef(UUID id) {
    }
}
