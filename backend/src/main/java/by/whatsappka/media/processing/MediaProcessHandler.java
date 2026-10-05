package by.whatsappka.media.processing;

import by.whatsappka.platform.jobs.JobContext;
import by.whatsappka.platform.jobs.JobHandler;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Обработчик задания media.process. Ошибки хранилища выбрасываются наружу и повторяются очередью. */
@Component
public class MediaProcessHandler implements JobHandler {

    public static final String TYPE = "media.process";

    private final MediaProcessor processor;

    public MediaProcessHandler(MediaProcessor processor) {
        this.processor = processor;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(JobContext context) throws Exception {
        UUID mediaId = UUID.fromString(context.payload().get("mediaId").asText());
        processor.process(mediaId);
    }
}
