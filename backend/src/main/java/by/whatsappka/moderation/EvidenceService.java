package by.whatsappka.moderation;

import by.whatsappka.identity.audit.AuditService;
import by.whatsappka.platform.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Просмотр доказательства модератором. Каждый просмотр пишется в аудит в той же транзакции. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class EvidenceService {

    public static final String AUDIT_VIEWED = "REPORT_EVIDENCE_VIEWED";

    public record EvidenceView(UUID reportId, String targetKind, String reason, String status, Instant createdAt,
                               JsonNode snapshot) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public EvidenceService(JdbcTemplate jdbc, AuditService audit, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public EvidenceView view(UUID moderator, UUID reportId, String traceId) {
        List<EvidenceView> found = jdbc.query(ReportSql.REPORT_WITH_EVIDENCE, (rs, n) -> {
            String raw = rs.getString("snapshot");
            try {
                return new EvidenceView(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("target_kind"),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        raw == null ? null : mapper.readTree(raw));
            } catch (Exception e) {
                throw new IllegalStateException("Снимок жалобы повреждён", e);
            }
        }, reportId);
        if (found.isEmpty()) {
            throw ApiException.notFound();
        }
        audit.record(moderator, AUDIT_VIEWED, "report", reportId, null, traceId);
        return found.get(0);
    }
}
