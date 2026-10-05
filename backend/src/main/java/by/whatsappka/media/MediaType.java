package by.whatsappka.media;

/** Форматы, которые сервер распознаёт по содержимому. Заголовок Content-Type клиента им не доверяется. */
public enum MediaType {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp"),
    PDF("application/pdf"),
    TXT("text/plain");

    private final String mime;

    MediaType(String mime) {
        this.mime = mime;
    }

    public String mime() {
        return mime;
    }

    public boolean isRawText() {
        return this == TXT;
    }
}
