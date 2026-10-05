package by.whatsappka.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.FieldError;

@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MessageSource messageSource;

    public ApiExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> apiException(ApiException exception, HttpServletRequest request) {
        return ApiResponses.of(
                request,
                exception.status(),
                exception.code(),
                exception.detail(),
                exception.fieldErrors(),
                exception.retryAfter()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldErrorDetail> fields = new ArrayList<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.add(new FieldErrorDetail(error.getField(), message(error)));
        }
        return validation(request, fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> invalidMethod(HandlerMethodValidationException exception, HttpServletRequest request) {
        List<FieldErrorDetail> fields = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            String fallback = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable resolvable : result.getResolvableErrors()) {
                if (resolvable instanceof FieldError fieldError) {
                    fields.add(new FieldErrorDetail(fieldError.getField(), message(fieldError)));
                } else {
                    fields.add(new FieldErrorDetail(fallback == null ? "request" : fallback, message(resolvable)));
                }
            }
        });
        return validation(request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldErrorDetail> fields = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String path = violation.getPropertyPath() == null ? "request" : violation.getPropertyPath().toString();
            fields.add(new FieldErrorDetail(path, violation.getMessage()));
        }
        return validation(request, fields);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> malformed(Exception exception, HttpServletRequest request) {
        String detail = "Неверный запрос";
        if (exception instanceof MissingServletRequestParameterException missing) {
            detail = "Не передан параметр " + missing.getParameterName();
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch && mismatch.getName() != null) {
            detail = "Параметр " + mismatch.getName() + " имеет неверный формат";
        } else if (exception instanceof HttpMessageNotReadableException) {
            detail = "Тело запроса не удалось прочитать";
        }
        return ApiResponses.of(request, HttpStatus.BAD_REQUEST, "bad_request", detail, List.of(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(HttpServletRequest request) {
        return status(request, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMedia(HttpServletRequest request) {
        return status(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(HttpServletRequest request) {
        return status(request, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler({NoResourceFoundException.class})
    public ResponseEntity<ApiError> missing(HttpServletRequest request) {
        return status(request, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        return status(request, exception.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Необработанная ошибка {}", TraceIds.current(request), exception);
        return status(request, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiError> validation(HttpServletRequest request, List<FieldErrorDetail> fields) {
        return ApiResponses.of(
                request,
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "Проверьте поля запроса",
                fields,
                null
        );
    }

    private ResponseEntity<ApiError> status(HttpServletRequest request, org.springframework.http.HttpStatusCode status) {
        return ApiResponses.of(
                request,
                status,
                ApiResponses.codeFor(status),
                ApiResponses.detailFor(status),
                List.of(),
                null
        );
    }

    private String message(MessageSourceResolvable resolvable) {
        try {
            String resolved = messageSource.getMessage(resolvable, LocaleContextHolder.getLocale());
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        } catch (NoSuchMessageException ignored) {
            // Ниже используется сообщение самого ограничения.
        }
        String fallback = resolvable.getDefaultMessage();
        return fallback == null || fallback.isBlank() ? "Некорректное значение" : fallback;
    }
}
