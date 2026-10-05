package by.whatsappka.platform.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Включается только в процессе worker: планировщик и пул исполнения заданий. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(JobProperties.class)
@ConditionalOnProperty(prefix = "whatsappka.jobs", name = "worker-enabled", havingValue = "true")
public class JobWorkerConfiguration {

    @Bean
    public ThreadPoolTaskExecutor jobExecutor(JobProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        // Очереди нет: захватывается не больше свободных слотов, поэтому отказа исполнителя не возникает.
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("job-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
