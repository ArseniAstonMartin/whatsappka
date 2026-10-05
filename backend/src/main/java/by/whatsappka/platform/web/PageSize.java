package by.whatsappka.platform.web;

public final class PageSize {

    public static final int DEFAULT = 20;
    public static final int MAX = 50;

    private PageSize() {
    }

    public static int limit(Integer requested) {
        if (requested == null) {
            return DEFAULT;
        }
        if (requested < 1) {
            throw ApiException.badRequest("invalid_page_size", "Размер страницы должен быть не меньше 1");
        }
        return Math.min(requested, MAX);
    }
}
