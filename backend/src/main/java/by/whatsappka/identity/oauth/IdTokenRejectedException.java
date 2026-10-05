package by.whatsappka.identity.oauth;

/** ID token не прошёл проверку подписи или утверждений. Причину наружу не раскрываем. */
public class IdTokenRejectedException extends RuntimeException {

    public IdTokenRejectedException() {
        super("ID token отклонён");
    }
}
