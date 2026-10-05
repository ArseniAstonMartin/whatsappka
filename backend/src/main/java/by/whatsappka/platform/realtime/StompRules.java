package by.whatsappka.platform.realtime;

import java.util.regex.Pattern;

/**
 * Единственный источник правил STOMP. Клиент может подписаться только на свои очереди и отправлять
 * только разрешённые команды. Публикация в брокерные назначения и чужие очереди запрещены.
 */
final class StompRules {

    static final String EVENTS_QUEUE = "/user/queue/events";
    static final String ERRORS_QUEUE = "/user/queue/errors";

    private static final Pattern CONVERSATION_COMMAND = Pattern.compile(
            "/app/conversations/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/(messages|typing|read)");

    private StompRules() {
    }

    static boolean subscribable(String destination) {
        return EVENTS_QUEUE.equals(destination) || ERRORS_QUEUE.equals(destination);
    }

    static boolean sendable(String destination) {
        return destination != null && CONVERSATION_COMMAND.matcher(destination).matches();
    }
}
