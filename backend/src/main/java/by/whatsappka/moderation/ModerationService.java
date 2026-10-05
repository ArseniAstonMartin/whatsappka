package by.whatsappka.moderation;

import by.whatsappka.identity.audit.AuditService;
import by.whatsappka.notifications.NotificationService;
import by.whatsappka.notifications.NotificationType;
import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Очередь жалоб и решения. Каждое решение — одна транзакция: блокировка жалобы, скрытие объекта, смена статуса,
 * запись в moderation_actions, аудит и уведомления. Если что-то падает, не остаётся ни скрытия, ни записи решения.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ModerationService {

    public record QueueItem(UUID id, String targetKind, String reason, String status, Instant createdAt,
                            UUID assigneeId, Instant decidedAt) {
    }

    public record Outcome(UUID reportId, String status) {
    }

    private record Report(UUID reporterId, String status, UUID assigneeId, ReportRules.TargetKind kind, UUID targetId) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final NotificationService notifications;

    public ModerationService(JdbcTemplate jdbc, AuditService audit, NotificationService notifications) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public CursorPage<QueueItem> queue(String rawStatus, String cursor, Integer limit) {
        ModerationRules.Status status = ModerationRules.parseStatus(rawStatus);
        int size = PageSize.limit(limit);
        Keyset key = decode(cursor);
        List<QueueItem> rows = key == null
                ? jdbc.query(ModerationSql.QUEUE_FIRST, ROW, status.name(), size + 1)
                : jdbc.query(ModerationSql.QUEUE_AFTER, ROW, status.name(), Timestamp.from(key.at()), key.id(), size + 1);
        boolean more = rows.size() > size;
        List<QueueItem> shown = new ArrayList<>(more ? rows.subList(0, size) : rows);
        String next = more ? encode(shown.get(shown.size() - 1).createdAt(), shown.get(shown.size() - 1).id()) : null;
        return new CursorPage<>(shown, next, more);
    }

    /** Взять в работу. Повтор тем же модератором — без изменений; чужая жалоба в работе — конфликт. */
    @Transactional
    public Outcome take(UUID moderator, UUID reportId, String traceId) {
        Report report = lock(reportId);
        if (report.status().equals("IN_REVIEW") && moderator.equals(report.assigneeId())) {
            return new Outcome(reportId, "IN_REVIEW");
        }
        if (report.status().equals("RESOLVED") || report.status().equals("REJECTED")) {
            throw conflict("already_decided", "Жалоба уже решена");
        }
        if (report.status().equals("IN_REVIEW")) {
            throw conflict("already_taken", "Жалобу уже взял в работу другой модератор");
        }
        jdbc.update(ModerationSql.TAKE, moderator, reportId);
        action(reportId, moderator, "TAKE", "OPEN", "IN_REVIEW", null, traceId);
        audit.record(moderator, "REPORT_TAKEN", "report", reportId, null, traceId);
        return new Outcome(reportId, "IN_REVIEW");
    }

    /** Решение «скрыть» или «не нарушение». Скрытие и решение — одна транзакция с уведомлениями. */
    @Transactional
    public Outcome resolve(UUID moderator, UUID reportId, String rawDecision, String rawReason, String traceId) {
        ModerationRules.Decision decision = ModerationRules.parseDecision(rawDecision);
        String reason = ModerationRules.requireReason(rawReason);
        Report report = requireAssigned(lock(reportId), moderator);
        UUID author = null;
        if (decision == ModerationRules.Decision.HIDE) {
            if (!ModerationRules.canHide(report.kind())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "hide_unsupported",
                        "Этот вид объекта модерация скрыть не может", List.of(), null);
            }
            author = hide(report.kind(), report.targetId(), moderator, reportId, reason, traceId);
        }
        String status = "RESOLVED";
        decide(reportId, moderator, status, decision == ModerationRules.Decision.HIDE ? "RESOLVE_HIDE" : "RESOLVE_NO_ACTION",
                reason, traceId);
        audit.record(moderator, "REPORT_RESOLVED", "report", reportId, reason, traceId);
        notifyOutcome(report.reporterId(), author, reportId);
        return new Outcome(reportId, status);
    }

    @Transactional
    public Outcome reject(UUID moderator, UUID reportId, String rawReason, String traceId) {
        String reason = ModerationRules.requireReason(rawReason);
        Report report = requireAssigned(lock(reportId), moderator);
        decide(reportId, moderator, "REJECTED", "REJECT", reason, traceId);
        audit.record(moderator, "REPORT_REJECTED", "report", reportId, reason, traceId);
        notifyOutcome(report.reporterId(), null, reportId);
        return new Outcome(reportId, "REJECTED");
    }

    /**
     * Восстановление скрытого. Доступно только администратору (маршрут /admin). Возможно, только пока последнее
     * действие по жалобе — скрытие и объект всё ещё скрыт; повтор не находит скрытого и отвечает конфликтом.
     */
    @Transactional
    public Outcome restore(UUID admin, UUID reportId, String rawReason, String traceId) {
        String reason = ModerationRules.requireReason(rawReason);
        Report report = lock(reportId);
        if (!report.status().equals("RESOLVED")) {
            throw conflict("nothing_to_restore", "Восстанавливать можно только объект, скрытый по решению");
        }
        List<String> last = jdbc.queryForList(ModerationSql.LAST_HIDE_ACTION, String.class, reportId);
        if (last.isEmpty() || !last.get(0).equals("RESOLVE_HIDE")) {
            throw conflict("nothing_to_restore", "По этой жалобе объект не скрывался или уже восстановлен");
        }
        restoreTarget(report.kind(), report.targetId(), admin, reportId, reason, traceId);
        action(reportId, admin, "RESTORE", "RESOLVED", "RESOLVED", reason, traceId);
        return new Outcome(reportId, "RESOLVED");
    }

    private UUID hide(ReportRules.TargetKind kind, UUID targetId, UUID moderator, UUID reportId, String reason, String traceId) {
        int changed;
        UUID author;
        String objectType;
        switch (kind) {
            case POST -> {
                changed = jdbc.update(ModerationSql.HIDE_POST, targetId);
                author = jdbc.queryForObject(ModerationSql.AUTHOR_OF_POST, UUID.class, targetId);
                objectType = "post";
            }
            case COMMENT -> {
                changed = jdbc.update(ModerationSql.HIDE_COMMENT, targetId);
                author = jdbc.queryForObject(ModerationSql.AUTHOR_OF_COMMENT, UUID.class, targetId);
                objectType = "comment";
            }
            case GROUP -> {
                changed = jdbc.update(ModerationSql.HIDE_GROUP, targetId);
                author = jdbc.queryForObject(ModerationSql.OWNER_OF_GROUP, UUID.class, targetId);
                objectType = "group";
            }
            default -> throw new IllegalStateException("hide is not supported for " + kind);
        }
        // Объект уже скрыт другим решением или удалён: решение всё равно фиксируется, но скрытие не повторяется.
        audit.record(moderator, "CONTENT_HIDDEN", objectType, targetId, reason + (changed == 0 ? " (уже скрыто)" : ""), traceId);
        return author;
    }

    private void restoreTarget(ReportRules.TargetKind kind, UUID targetId, UUID admin, UUID reportId, String reason, String traceId) {
        int changed = switch (kind) {
            case POST -> jdbc.update(ModerationSql.RESTORE_POST, targetId);
            case COMMENT -> jdbc.update(ModerationSql.RESTORE_COMMENT, targetId);
            case GROUP -> jdbc.update(ModerationSql.RESTORE_GROUP, targetId);
            default -> throw new IllegalStateException("restore is not supported for " + kind);
        };
        if (changed == 0) {
            throw conflict("not_hidden", "Объект сейчас не скрыт");
        }
        audit.record(admin, "CONTENT_RESTORED", kind.name().toLowerCase(), targetId, reason, traceId);
    }

    private void notifyOutcome(UUID reporter, UUID author, UUID reportId) {
        notifications.notifyModeration(reporter, "report:" + reportId + ":result", NotificationType.MODERATION_RESULT, reportId);
        if (author != null) {
            notifications.notifyModeration(author, "report:" + reportId + ":hidden", NotificationType.CONTENT_HIDDEN, reportId);
        }
    }

    private void decide(UUID reportId, UUID moderator, String status, String action, String reason, String traceId) {
        if (jdbc.update(ModerationSql.DECIDE, status, reportId, moderator) != 1) {
            throw conflict("already_decided", "Жалоба уже решена или назначена другому модератору");
        }
        action(reportId, moderator, action, "IN_REVIEW", status, reason, traceId);
    }

    private void action(UUID reportId, UUID actor, String action, String from, String to, String reason, String traceId) {
        jdbc.update(ModerationSql.INSERT_ACTION, UUID.randomUUID(), reportId, actor, action, from, to, reason, traceId);
    }

    private Report lock(UUID reportId) {
        List<Report> rows = jdbc.query(ModerationSql.LOCK_REPORT, (rs, n) -> {
            ReportRules.TargetKind kind = ReportRules.TargetKind.valueOf(rs.getString("target_kind"));
            UUID target = switch (kind) {
                case USER -> uuid(rs, "target_user_id");
                case POST -> uuid(rs, "target_post_id");
                case COMMENT -> uuid(rs, "target_comment_id");
                case MESSAGE -> uuid(rs, "target_message_id");
                case GROUP -> uuid(rs, "target_group_id");
            };
            return new Report(uuid(rs, "reporter_id"), rs.getString("status"), uuid(rs, "assignee_id"), kind, target);
        }, reportId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        return rows.get(0);
    }

    private static Report requireAssigned(Report report, UUID moderator) {
        if (report.status().equals("OPEN")) {
            throw conflict("not_in_review", "Сначала возьмите жалобу в работу");
        }
        if (report.status().equals("RESOLVED") || report.status().equals("REJECTED")) {
            throw conflict("already_decided", "Жалоба уже решена");
        }
        if (!moderator.equals(report.assigneeId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_assignee", "Решение принимает модератор, взявший жалобу", List.of(), null);
        }
        return report;
    }

    private static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail, List.of(), null);
    }

    private static UUID uuid(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static final org.springframework.jdbc.core.RowMapper<QueueItem> ROW = (rs, n) -> new QueueItem(
            UUID.fromString(rs.getString("id")),
            rs.getString("target_kind"),
            rs.getString("reason"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            uuid(rs, "assignee_id"),
            rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant());

    private record Keyset(Instant at, UUID id) {
    }

    static String encode(Instant at, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((at.toString() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    static Keyset decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            return new Keyset(Instant.parse(raw.substring(0, separator)), UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException malformed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_cursor", "Курсор страницы некорректен", List.of(), null);
        }
    }
}
