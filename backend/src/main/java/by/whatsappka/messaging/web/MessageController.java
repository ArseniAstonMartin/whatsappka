package by.whatsappka.messaging.web;

import by.whatsappka.identity.security.AuthenticatedUser;
import by.whatsappka.messaging.MessageEditService;
import by.whatsappka.messaging.MessageEventQueries;
import by.whatsappka.messaging.MessageQueries;
import by.whatsappka.platform.web.ApiV1Controller;
import by.whatsappka.platform.web.CursorPage;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MessageController {

    private final MessageQueries messages;
    private final MessageEditService edits;
    private final MessageEventQueries events;

    public MessageController(MessageQueries messages, MessageEditService edits, MessageEventQueries events) {
        this.messages = messages;
        this.edits = edits;
        this.events = events;
    }

    /** История чата: новые сообщения первыми, по 50. cursor — seq последнего показанного. */
    @GetMapping("/conversations/{id}/messages")
    public CursorPage<MessageQueries.MessageView> history(
            @PathVariable("id") UUID conversationId,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return messages.history(viewer.userId(), conversationId, cursor, limit);
    }

    /** Правка текста автором в течение суток. seq не меняется, version растёт. */
    @PatchMapping("/conversations/{id}/messages/{messageId}")
    public EditResponse edit(
            @PathVariable("id") UUID conversationId,
            @PathVariable("messageId") UUID messageId,
            @RequestBody EditRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        var edited = edits.edit(viewer.userId(), conversationId, messageId, request.body());
        edits.notifyEdited(conversationId, edited, request.body());
        return new EditResponse(edited.id(), edited.version(), edited.updatedAt());
    }

    /** Удаление для всех. Модератор удаляет чужое только с причиной (reason). */
    @DeleteMapping("/conversations/{id}/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("id") UUID conversationId,
            @PathVariable("messageId") UUID messageId,
            @RequestBody(required = false) DeleteRequest request,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        var deleted = edits.delete(viewer.userId(), conversationId, messageId, request == null ? null : request.reason());
        edits.notifyDeleted(conversationId, deleted);
    }

    /**
     * Журнал событий для синхронизации. cursor — номер последнего применённого события.
     * Ответ с fullSyncRequired=true означает, что нужно перечитать чат целиком.
     */
    @GetMapping("/conversations/{id}/events")
    public MessageEventQueries.EventsPage events(
            @PathVariable("id") UUID conversationId,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return events.events(viewer.userId(), conversationId, cursor, limit);
    }

    /** Текущая граница журнала событий: с неё клиент начинает синхронизацию. */
    @GetMapping("/conversations/{id}/events/head")
    public MessageEventQueries.Head eventsHead(@PathVariable("id") UUID conversationId, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return events.head(viewer.userId(), conversationId);
    }

    public record EditRequest(String body) {
    }

    public record DeleteRequest(String reason) {
    }

    public record EditResponse(UUID id, long version, Instant updatedAt) {
    }
}
