package by.whatsappka.platform.idempotency;

public record IdempotentResponse(int status, Object body) {

    public IdempotentResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("Статус ответа вне диапазона HTTP");
        }
    }
}
