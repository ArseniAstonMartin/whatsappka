package by.whatsappka.admin;

import by.whatsappka.identity.audit.AuditService;
import by.whatsappka.platform.jobs.JobQueue;
import by.whatsappka.platform.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Операторские действия над заданиями. Повтор и запись в аудит — в одной транзакции. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JobAdministration {

    private final JobQueue queue;
    private final AuditService audit;

    public JobAdministration(JobQueue queue, AuditService audit) {
        this.queue = queue;
        this.audit = audit;
    }

    @Transactional
    public void retryFailed(UUID actorId, UUID jobId, String traceId) {
        if (!queue.retryFailed(jobId)) {
            // Нет задания или оно не в FAILED: повтор недоступен. Различать эти случаи наружу не нужно.
            throw new ApiException(HttpStatus.CONFLICT, "job_not_failed",
                    "Повтор доступен только для заданий в статусе FAILED", List.of(), null);
        }
        audit.record(actorId, "JOB_RETRIED", "background_job", jobId, "Оператор запустил повтор задания", traceId);
    }
}
