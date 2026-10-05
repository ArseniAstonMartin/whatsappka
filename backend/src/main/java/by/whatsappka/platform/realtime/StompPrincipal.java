package by.whatsappka.platform.realtime;

import java.security.Principal;
import java.util.UUID;

/** Имя принципала — id пользователя: по нему адресуются его очереди /user/queue. */
public record StompPrincipal(UUID userId) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
