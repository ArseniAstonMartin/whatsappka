package by.whatsappka.platform.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Страница списка. nextCursor пустой, когда следующей страницы нет.")
public record CursorPage<T>(
        List<T> items,
        @Schema(nullable = true) String nextCursor,
        boolean hasMore
) {
    public CursorPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
