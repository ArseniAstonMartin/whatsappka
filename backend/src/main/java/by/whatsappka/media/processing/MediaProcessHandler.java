package by.whatsappka.media.processing;

import by.whatsappka.platform.jobs.JobContext;
import by.whatsappka.platform.jobs.JobHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Обработчик задания media.process. Ошибки хранилища выбрасываются наружу и повторяются очередью. */
@Component
public class MediaProcessHandler implements JobHandler {

    public static final String TYPE = "media.process";

    private final MediaProcessor processor;
    private final MeterRegistry metrics;

    public MediaProcessHandler(MediaProcessor processor, MeterRegistry metrics) {
        this.processor = processor;
        this.metrics = metrics;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(JobContext context) throws Exception {
        UUID mediaId = UUID.fromString(context.payload().get("mediaId").asText());
        // Время превью — метрика задержки: по нему видно, успевает ли worker за загрузками.
        Timer.Sample sample = Timer.start(metrics);
        String outcome = "ok";
        try {
            processor.process(mediaId);
        } catch (Exception e) {
            outcome = "error";
            throw e;
        } finally {
            sample.stop(metrics.timer("whatsappka.media.preview", "outcome", outcome));
        }
    }
}
