package by.whatsappka.platform.web;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

public record CursorPageQuery(
        @Parameter(description = "Курсор следующей страницы")
        String cursor,
        @Parameter(description = "Размер страницы. Сервер не возвращает больше 50 элементов.")
        @Schema(minimum = "1", maximum = "50", defaultValue = "20")
        Integer limit
) {
    public String normalizedCursor() {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        return cursor.trim();
    }

    public int normalizedLimit() {
        return PageSize.limit(limit);
    }
}
