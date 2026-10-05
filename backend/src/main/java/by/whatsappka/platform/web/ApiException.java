package by.whatsappka.platform.web;

import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String detail;
    private final List<FieldErrorDetail> fieldErrors;
    private final Duration retryAfter;

    public ApiException(
            HttpStatus status,
            String code,
            String detail,
            List<FieldErrorDetail> fieldErrors,
            Duration retryAfter
    ) {
        super(detail);
        this.status = status;
        this.code = code;
        this.detail = detail;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        this.retryAfter = retryAfter;
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail, List.of(), null);
    }

    public static ApiException validation(String detail, List<FieldErrorDetail> fieldErrors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", detail, fieldErrors, null);
    }

    public static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Нужна действующая сессия", List.of(), null);
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Действие запрещено", List.of(), null);
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", "Объект не найден", List.of(), null);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "conflict", detail, List.of(), null);
    }

    public static ApiException payloadTooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "payload_too_large", "Запрос превышает допустимый размер", List.of(), null);
    }

    public static ApiException unsupportedMediaType() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Тип содержимого не поддерживается", List.of(), null);
    }

    public static ApiException tooManyRequests(Duration retryAfter) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests", "Слишком много запросов", List.of(), retryAfter);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String detail() {
        return detail;
    }

    public List<FieldErrorDetail> fieldErrors() {
        return fieldErrors;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
