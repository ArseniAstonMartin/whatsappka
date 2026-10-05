package by.whatsappka.platform.web;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        HttpStatusCode status = status(request);
        Object thrown = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (thrown instanceof ApiException apiException) {
            return ApiResponses.of(
                    request,
                    apiException.status(),
                    apiException.code(),
                    apiException.detail(),
                    apiException.fieldErrors(),
                    apiException.retryAfter()
            );
        }
        return ApiResponses.of(
                request,
                status,
                ApiResponses.codeFor(status),
                ApiResponses.detailFor(status),
                java.util.List.of(),
                null
        );
    }

    private static HttpStatusCode status(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (value instanceof Integer code) {
            try {
                return HttpStatus.valueOf(code);
            } catch (IllegalArgumentException ignored) {
                return HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
