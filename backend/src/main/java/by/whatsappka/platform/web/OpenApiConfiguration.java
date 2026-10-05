package by.whatsappka.platform.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OpenApiConfiguration {

    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI whatsappkaOpenApi() {
        Schema<?> fieldError = new Schema<>()
                .type("object")
                .addProperty("field", new StringSchema().example("email"))
                .addProperty("message", new StringSchema().example("Обязательное поле"));
        fieldError.setRequired(List.of("field", "message"));

        Schema<?> apiError = new Schema<>()
                .type("object")
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("code", new StringSchema().example("validation_failed"))
                .addProperty("detail", new StringSchema().example("Проверьте поля запроса"))
                .addProperty("fieldErrors", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/FieldError")))
                .addProperty("traceId", new StringSchema().format("uuid"));
        apiError.setRequired(List.of("status", "code", "detail", "fieldErrors", "traceId"));

        return new OpenAPI()
                .info(new Info()
                        .title("Ватсапка")
                        .version("v1")
                        .description("HTTP API. Даты в UTC, ISO 8601. Идентификаторы — UUID строкой."))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access JWT"))
                        .addSchemas("FieldError", fieldError)
                        .addSchemas("ApiError", apiError))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public OperationCustomizer standardErrorResponses() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            if (operation.getResponses() == null) {
                operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            }
            addError(operation, "400", "Неверный запрос");
            addError(operation, "401", "Нет действующей сессии");
            addError(operation, "403", "Действие запрещено");
            addError(operation, "404", "Объект не найден");
            addError(operation, "409", "Конфликт состояния");
            addError(operation, "413", "Слишком большой запрос");
            addError(operation, "415", "Неподдерживаемый тип содержимого");
            addError(operation, "429", "Слишком много запросов");
            addError(operation, "500", "Внутренняя ошибка");
            if (isPublic(handlerMethod)) {
                operation.setSecurity(List.of());
            }
            return operation;
        };
    }

    private static boolean isPublic(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(PublicApi.class)
                || handlerMethod.getBeanType().isAnnotationPresent(PublicApi.class);
    }

    private static void addError(Operation operation, String status, String description) {
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/ApiError");
        MediaType mediaType = new MediaType().schema(schema);
        Content content = new Content().addMediaType(ApiResponses.PROBLEM_JSON.toString(), mediaType);
        operation.getResponses().addApiResponse(status, new ApiResponse().description(description).content(content));
    }
}
