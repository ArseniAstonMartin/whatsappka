package by.whatsappka.identity.oauth;

/** Google не ответил или ответ не удалось разобрать. */
public class GoogleUnavailableException extends RuntimeException {

    public GoogleUnavailableException() {
        super("Google недоступен");
    }
}
