package by.whatsappka.media;

import org.springframework.http.HttpStatus;

/** Отказ в загрузке с кодом для клиента. Текст не раскрывает внутренних деталей. */
public class MediaRejectedException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public MediaRejectedException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
