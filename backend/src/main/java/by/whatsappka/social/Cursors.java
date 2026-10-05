package by.whatsappka.social;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.http.HttpStatus;

/** Курсоры keyset-пагинации списков связей: непрозрачная строка из времени и id. */
final class Cursors {

    record Keyset(Instant at, UUID id) {
    }

    /** Строка страницы: идентификатор пользователя, имя, отображаемое имя и время связи. */
    record Row(UUID id, String username, String displayName, Instant at) {
    }

    private Cursors() {
    }

    static String encode(Instant at, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((at.toString() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    static Keyset decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            return new Keyset(Instant.parse(raw.substring(0, separator)), UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException malformed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_cursor", "Курсор страницы некорректен",
                    List.of(), null);
        }
    }

    /**
     * Загружает страницу: запрашивает на одну строку больше размера, чтобы понять, есть ли следующая.
     */
    static CursorPage<FollowQueries.UserSummary> page(
            String cursor,
            int size,
            BiFunction<Keyset, Integer, List<Row>> loader
    ) {
        Keyset key = decode(cursor);
        List<Row> rows = loader.apply(key, size + 1);
        boolean more = rows.size() > size;
        List<Row> shown = more ? rows.subList(0, size) : rows;
        List<FollowQueries.UserSummary> items = shown.stream()
                .map((row) -> new FollowQueries.UserSummary(row.id(), row.username(), row.displayName()))
                .toList();
        String next = null;
        if (more) {
            Row last = shown.get(shown.size() - 1);
            next = encode(last.at(), last.id());
        }
        return new CursorPage<>(items, next, more);
    }
}
