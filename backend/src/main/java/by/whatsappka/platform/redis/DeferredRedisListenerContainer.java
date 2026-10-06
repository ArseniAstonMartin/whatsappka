package by.whatsappka.platform.redis;

import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Контейнер подписки, который не стартует вместе с контекстом: включает его {@link RedisListenerStarter}. */
public class DeferredRedisListenerContainer extends RedisMessageListenerContainer {

    @Override
    public boolean isAutoStartup() {
        return false;
    }
}
