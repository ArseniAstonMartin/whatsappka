package by.whatsappka.content;

import by.whatsappka.platform.jobs.JobContext;
import by.whatsappka.platform.jobs.JobHandler;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Обработчик задания post.publish (TASK-043). Ошибки хранилища/БД выбрасываются наружу и повторяются очередью. */
@Component
public class PostPublishJobHandler implements JobHandler {

    public static final String TYPE = PostSchedulingService.JOB_TYPE;

    private final PostSchedulingService scheduling;

    public PostPublishJobHandler(PostSchedulingService scheduling) {
        this.scheduling = scheduling;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(JobContext context) throws Exception {
        UUID postId = UUID.fromString(context.payload().get("postId").asText());
        scheduling.publishScheduled(postId);
    }
}
