package by.whatsappka.content;

import by.whatsappka.identity.account.UserAccount;
import by.whatsappka.identity.account.UserAccountRepository;
import by.whatsappka.platform.jobs.JobQueue;
import by.whatsappka.platform.outbox.OutboxWriter;
import by.whatsappka.platform.web.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Расписание публикации через очередь фоновых заданий (TASK-043). {@link #schedule} и
 * {@link #cancelSchedule} вызываются из HTTP; {@link #publishScheduled} вызывает worker вне
 * HTTP-контекста, поэтому сервис не зависит от бинов, доступных только профилю backend
 * (worker поднимается с {@code web-application-type: none} — см. application-worker.yml), и
 * проверяет членство в группе собственным запросом, а не через {@code CommunityMembershipService}.
 */
@Service
public class PostSchedulingService {

    static final String JOB_TYPE = "post.publish";

    private final JdbcTemplate jdbc;
    private final JobQueue jobs;
    private final OutboxWriter outbox;
    private final UserAccountRepository users;
    private final Clock clock;

    public PostSchedulingService(JdbcTemplate jdbc, JobQueue jobs, OutboxWriter outbox, UserAccountRepository users, Clock clock) {
        this.jdbc = jdbc;
        this.jobs = jobs;
        this.outbox = outbox;
        this.users = users;
        this.clock = clock;
    }

    /** DRAFT → SCHEDULED, либо перенос времени уже отложенной записи. Задание ставится в той же транзакции. */
    @Transactional
    public void schedule(UUID postId, UUID authorId, Instant publishAt) {
        PostRules.validateScheduleTime(publishAt, clock.instant());
        int updated = jdbc.update(
                "UPDATE posts SET status = 'SCHEDULED', version = version + 1, updated_at = ?, schedule_failure_reason = NULL "
                        + "WHERE id = ? AND author_id = ? AND status IN ('DRAFT', 'SCHEDULED') AND deleted_at IS NULL",
                Timestamp.from(clock.instant()), postId, authorId);
        if (updated == 0) {
            diagnoseScheduleFailure(postId, authorId);
        }
        String dedupKey = dedupKey(postId);
        jobs.cancel(dedupKey);
        jobs.clearFinished(dedupKey);
        if (!jobs.enqueue(JOB_TYPE, dedupKey, Map.of("postId", postId.toString()), publishAt)) {
            throw new ApiException(HttpStatus.CONFLICT, "schedule_in_progress",
                    "Публикация уже выполняется, повторите через минуту", List.of(), null);
        }
    }

    /** SCHEDULED → DRAFT, задание снимается из очереди. */
    @Transactional
    public void cancelSchedule(UUID postId, UUID authorId) {
        int updated = jdbc.update(
                "UPDATE posts SET status = 'DRAFT', version = version + 1, updated_at = ? "
                        + "WHERE id = ? AND author_id = ? AND status = 'SCHEDULED' AND deleted_at IS NULL",
                Timestamp.from(clock.instant()), postId, authorId);
        if (updated == 0) {
            diagnoseCancelFailure(postId, authorId);
        }
        jobs.cancel(dedupKey(postId));
    }

    /**
     * Вызывается обработчиком задания. Повторно проверяет содержимое, группу и активность автора;
     * недопустимая запись возвращается в DRAFT с причиной, задание при этом считается выполненным
     * (повторять нечего). Отменённая или уже обработанная запись — no-op: двойной публикации не будет.
     */
    @Transactional
    public void publishScheduled(UUID postId) {
        List<ScheduledRow> rows = jdbc.query(
                "SELECT author_id, group_id, body, status FROM posts WHERE id = ? AND deleted_at IS NULL",
                (rs, n) -> new ScheduledRow(
                        UUID.fromString(rs.getString("author_id")),
                        rs.getObject("group_id") == null ? null : UUID.fromString(rs.getString("group_id")),
                        rs.getString("body"), rs.getString("status")),
                postId);
        if (rows.isEmpty() || !"SCHEDULED".equals(rows.get(0).status())) {
            return;
        }
        ScheduledRow row = rows.get(0);
        String reason = invalidReason(postId, row);
        if (reason != null) {
            jdbc.update(
                    "UPDATE posts SET status = 'DRAFT', version = version + 1, updated_at = ?, schedule_failure_reason = ? "
                            + "WHERE id = ? AND status = 'SCHEDULED'",
                    Timestamp.from(clock.instant()), reason, postId);
            return;
        }
        Instant now = clock.instant();
        int updated = jdbc.update(
                "UPDATE posts SET status = 'PUBLISHED', published_at = ?, version = version + 1, updated_at = ?, "
                        + "schedule_failure_reason = NULL WHERE id = ? AND status = 'SCHEDULED'",
                Timestamp.from(now), Timestamp.from(now), postId);
        if (updated == 0) {
            return;
        }
        outbox.record("post", postId, PostService.EVENT_PUBLISHED,
                Map.of("postId", postId.toString(), "authorId", row.authorId().toString()));
    }

    /** null — можно публиковать; иначе причина, по которой запись возвращается в DRAFT. */
    private String invalidReason(UUID postId, ScheduledRow row) {
        Boolean hasMedia = jdbc.queryForObject("SELECT count(*) > 0 FROM post_media WHERE post_id = ?", Boolean.class, postId);
        if ((row.body() == null || row.body().isBlank()) && !Boolean.TRUE.equals(hasMedia)) {
            return "empty_post";
        }
        if (row.groupId() != null && !isActiveGroupMember(row.groupId(), row.authorId())) {
            return "not_group_member";
        }
        boolean authorActive = users.findById(row.authorId()).map(UserAccount::isActive).orElse(false);
        if (!authorActive) {
            return "author_inactive";
        }
        return null;
    }

    private boolean isActiveGroupMember(UUID groupId, UUID userId) {
        Boolean ownerMatch = jdbc.query(
                "SELECT owner_id = ? AS is_owner FROM groups WHERE id = ? AND deleted_at IS NULL",
                (rs, n) -> rs.getBoolean("is_owner"), userId, groupId).stream().findFirst().orElse(null);
        if (ownerMatch == null) {
            return false;
        }
        if (ownerMatch) {
            return true;
        }
        Boolean member = jdbc.queryForObject(
                "SELECT count(*) > 0 FROM group_members WHERE group_id = ? AND user_id = ?", Boolean.class, groupId, userId);
        return Boolean.TRUE.equals(member);
    }

    private void diagnoseScheduleFailure(UUID postId, UUID authorId) {
        List<String> status = jdbc.query(
                "SELECT status FROM posts WHERE id = ? AND author_id = ? AND deleted_at IS NULL",
                (rs, n) -> rs.getString("status"), postId, authorId);
        if (status.isEmpty()) {
            throw ApiException.notFound();
        }
        throw new ApiException(HttpStatus.CONFLICT, "schedule_not_allowed",
                "Запись опубликована — расписание недоступно", List.of(), null);
    }

    private void diagnoseCancelFailure(UUID postId, UUID authorId) {
        List<String> status = jdbc.query(
                "SELECT status FROM posts WHERE id = ? AND author_id = ? AND deleted_at IS NULL",
                (rs, n) -> rs.getString("status"), postId, authorId);
        if (status.isEmpty()) {
            throw ApiException.notFound();
        }
        throw new ApiException(HttpStatus.CONFLICT, "not_scheduled", "Запись не отложена", List.of(), null);
    }

    private static String dedupKey(UUID postId) {
        return JOB_TYPE + ":" + postId;
    }

    private record ScheduledRow(UUID authorId, UUID groupId, String body, String status) {
    }
}
