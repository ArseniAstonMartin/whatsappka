package by.whatsappka.moderation;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Подача жалобы: доступ проверяется тем же запросом, что и снимок. Недоступный объект неотличим от отсутствующего. */
@Service
public class ReportService {

    public record Submitted(UUID id, String status) {
    }

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Submitted submit(UUID reporter, String rawKind, UUID targetId, String rawReason, String rawDescription) {
        ReportRules.TargetKind kind = ReportRules.parseKind(rawKind);
        ReportRules.Reason reason = ReportRules.parseReason(rawReason);
        String description = ReportRules.normalizeDescription(rawDescription);
        if (targetId == null) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("targetId", "Укажите объект")));
        }
        if (kind == ReportRules.TargetKind.USER && targetId.equals(reporter)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "self_report", "Нельзя пожаловаться на себя", List.of(), null);
        }
        String snapshot = snapshot(kind, reporter, targetId);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(ReportSql.INSERT_REPORT, id, reporter, kind.name(),
                    kind == ReportRules.TargetKind.USER ? targetId : null,
                    kind == ReportRules.TargetKind.POST ? targetId : null,
                    kind == ReportRules.TargetKind.COMMENT ? targetId : null,
                    kind == ReportRules.TargetKind.MESSAGE ? targetId : null,
                    kind == ReportRules.TargetKind.GROUP ? targetId : null,
                    reason.name(), description);
        } catch (DuplicateKeyException duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "report_already_open",
                    "Вы уже отправили жалобу на этот объект, она ещё рассматривается", List.of(), null);
        }
        jdbc.update(ReportSql.INSERT_EVIDENCE, UUID.randomUUID(), id, snapshot);
        return new Submitted(id, "OPEN");
    }

    private String snapshot(ReportRules.TargetKind kind, UUID viewer, UUID targetId) {
        List<String> found = switch (kind) {
            case USER -> jdbc.queryForList(ReportSql.SNAPSHOT_USER, String.class, targetId, viewer, viewer);
            case POST -> jdbc.queryForList(ReportSql.SNAPSHOT_POST, String.class, targetId, viewer, viewer);
            case COMMENT -> jdbc.queryForList(ReportSql.SNAPSHOT_COMMENT, String.class, targetId, viewer, viewer);
            case MESSAGE -> jdbc.queryForList(ReportSql.SNAPSHOT_MESSAGE, String.class, viewer, targetId);
            case GROUP -> jdbc.queryForList(ReportSql.SNAPSHOT_GROUP, String.class, targetId, viewer, viewer);
        };
        if (found.isEmpty()) {
            throw ApiException.notFound();
        }
        return found.get(0);
    }
}
