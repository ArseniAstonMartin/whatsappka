package by.whatsappka.platform.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Подписка на сигналы Redis для процесса worker: сигнал ускоряет опрос, плановый опрос остаётся. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnProperty(prefix = "whatsappka.outbox", name = "relay-enabled", havingValue = "true")
public class OutboxRelayConfiguration {

    @Bean
    public RedisMessageListenerContainer outboxSignalListener(RedisConnectionFactory connections, OutboxRelay relay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connections);
        container.addMessageListener((message, pattern) -> relay.poll(), new ChannelTopic(OutboxSignals.CHANNEL));
        return container;
    }
}
