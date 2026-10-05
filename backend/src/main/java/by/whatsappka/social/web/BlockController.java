package by.whatsappka.social.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.social.BlockQueries;
import by.whatsappka.social.BlockService;
import by.whatsappka.social.FollowQueries;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BlockController {

    private final BlockService blocks;
    private final BlockQueries queries;

    public BlockController(BlockService blocks, BlockQueries queries) {
        this.blocks = blocks;
        this.queries = queries;
    }

    @PutMapping("/users/{id}/block")
    public ResponseEntity<Void> block(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        blocks.block(viewer.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/block")
    public ResponseEntity<Void> unblock(@PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        blocks.unblock(viewer.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/blocks")
    public CursorPage<FollowQueries.UserSummary> myBlocks(
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return queries.blocks(viewer.userId(), cursor, limit == null ? by.whatsappka.platform.web.PageSize.DEFAULT : limit);
    }
}
