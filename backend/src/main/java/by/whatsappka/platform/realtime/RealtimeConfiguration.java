package by.whatsappka.platform.realtime;

import by.whatsappka.identity.IdentityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/** STOMP поверх WebSocket с одним брокером в памяти процесса; отдельный брокер не нужен (PRD §5.1). */
@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RealtimeConfiguration implements WebSocketMessageBrokerConfigurer {

    /** Лимит кадра: 64 КиБ, как в PRD. Бинарные файлы передаются через REST. */
    static final int FRAME_LIMIT = 64 * 1024;
    /** Буфер отправки на соединение. При переполнении клиент отстал: соединение закрывается. */
    static final int SEND_BUFFER_LIMIT = 512 * 1024;
    static final int SEND_TIME_LIMIT_MS = 10_000;
    static final long HEARTBEAT_MS = 10_000;

    private final IdentityProperties identity;
    private final StompInboundGuard guard;
    private final StompConnections connections;

    public RealtimeConfiguration(IdentityProperties identity, StompInboundGuard guard, StompConnections connections) {
        this.identity = identity;
        this.guard = guard;
        this.connections = connections;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(identity.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeatScheduler());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(FRAME_LIMIT)
                .setSendBufferSizeLimit(SEND_BUFFER_LIMIT)
                .setSendTimeLimit(SEND_TIME_LIMIT_MS)
                .addDecoratorFactory(this::track);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(guard);
    }

    private WebSocketHandler track(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                connections.opened(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status)
                    throws Exception {
                connections.closed(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    @Bean
    public ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
