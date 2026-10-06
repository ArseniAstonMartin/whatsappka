package by.whatsappka.notifications;

import by.whatsappka.platform.redis.DeferredRedisListenerContainer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Подписка API на сигналы уведомлений от worker'а. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class NotificationRealtimeConfiguration {

    @Bean
    public RedisMessageListenerContainer notificationSignalListener(RedisConnectionFactory connections,
                                                                    NotificationRealtimeBridge bridge) {
        DeferredRedisListenerContainer container = new DeferredRedisListenerContainer();
        container.setConnectionFactory(connections);
        // Запуск по расписанию (RedisListenerStarter): недоступный Redis не должен не давать API стартовать.
        container.addMessageListener(bridge, new ChannelTopic(NotificationRealtimePublisher.CHANNEL));
        return container;
    }
}
