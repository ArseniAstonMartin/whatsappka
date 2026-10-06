package by.whatsappka.platform.health;

import by.whatsappka.media.storage.MediaProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/** MinIO: недоступность — деградация (медиа не загружаются, текст работает), а не причина перезапуска. */
@Component("minio")
public class MinioStatusIndicator implements HealthIndicator {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final S3Client s3;
    private final String bucket;
    private final DependencyStatus status;

    public MinioStatusIndicator(S3Client s3, MediaProperties properties, Clock clock) {
        this.s3 = s3;
        this.bucket = properties.bucket();
        this.status = new DependencyStatus(clock);
    }

    @Override
    public Health health() {
        return status.check(this::probe);
    }

    private Health probe() {
        try {
            // Короткий лимит на вызов: проверка не должна держать общий обход служебных эндпоинтов дольше нескольких секунд.
            s3.headBucket(HeadBucketRequest.builder()
                    .bucket(bucket)
                    .overrideConfiguration(AwsRequestOverrideConfiguration.builder().apiCallTimeout(PROBE_TIMEOUT).build())
                    .build());
            return Health.up().build();
        } catch (SdkException e) {
            return DependencyStatus.degraded(e.getClass().getSimpleName());
        }
    }
}
