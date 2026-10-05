package by.whatsappka.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ApiResponses {

    public static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    private ApiResponses() {
    }

    public static ResponseEntity<ApiError> of(
            HttpServletRequest request,
            HttpStatusCode status,
            String code,
            String detail,
            List<FieldErrorDetail> fieldErrors,
            Duration retryAfter
    ) {
        ApiError body = new ApiError(
                status.value(),
                code,
                detail,
                fieldErrors,
                TraceIds.current(request)
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).contentType(PROBLEM_JSON);
        if (retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero()) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.toSeconds()));
        }
        return builder.body(body);
    }

    public static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "bad_request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            case 409 -> "conflict";
            case 413 -> "payload_too_large";
            case 415 -> "unsupported_media_type";
            case 429 -> "too_many_requests";
            default -> status.is5xxServerError() ? "internal_error" : "request_failed";
        };
    }

    public static String detailFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Неверный запрос";
            case 401 -> "Нужна действующая сессия";
            case 403 -> "Действие запрещено";
            case 404 -> "Объект не найден";
            case 405 -> "Метод не поддерживается";
            case 409 -> "Конфликт состояния";
            case 413 -> "Запрос превышает допустимый размер";
            case 415 -> "Тип содержимого не поддерживается";
            case 429 -> "Слишком много запросов";
            default -> status.is5xxServerError() ? "Внутренняя ошибка" : "Запрос отклонён";
        };
    }
}
