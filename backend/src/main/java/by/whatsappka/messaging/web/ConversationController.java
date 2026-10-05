package by.whatsappka.messaging.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.messaging.ConversationQueries;
import by.whatsappka.messaging.ConversationService;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConversationController {

    private final ConversationService conversations;
    private final ConversationQueries queries;

    public ConversationController(ConversationService conversations, ConversationQueries queries) {
        this.conversations = conversations;
        this.queries = queries;
    }

    /** Создание или получение личного диалога: повтор возвращает тот же чат. 201 — создан, 200 — уже был. */
    @PostMapping("/conversations")
    public ResponseEntity<ConversationRef> openDirect(
            @RequestBody OpenDirectRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        ConversationService.Created result = conversations.openDirect(viewer.userId(), request.userId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new ConversationRef(result.conversationId()));
    }

    @GetMapping("/conversations")
    public CursorPage<ConversationQueries.ConversationView> list(
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return queries.list(viewer.userId(), cursor, limit == null ? PageSize.DEFAULT : limit);
    }

    @GetMapping("/conversations/{id}")
    public ConversationQueries.ConversationView get(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return queries.get(viewer.userId(), id);
    }

    public record OpenDirectRequest(UUID userId) {
    }

    public record ConversationRef(UUID id) {
    }
}
