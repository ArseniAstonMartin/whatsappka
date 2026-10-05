package by.whatsappka.moderation;

import by.whatsappka.platform.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Причина скрытия для автора его публикации. Видна только автору скрытого поста и только текст решения:
 * ни личность заявителя, ни сама жалоба автору не отдаются.
 */
@Service
@ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class AuthorNoticeService {

    public record HiddenPostNotice(UUID postId, String reason) {
    }

    private final JdbcTemplate jdbc;

    public AuthorNoticeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public HiddenPostNotice hiddenPost(UUID viewer, UUID postId) {
        Boolean ownHidden = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM posts WHERE id = ? AND author_id = ? AND status = 'HIDDEN')",
                Boolean.class, postId, viewer);
        if (!Boolean.TRUE.equals(ownHidden)) {
            throw ApiException.notFound();
        }
        List<String> reasons = jdbc.queryForList(
                "SELECT ma.reason FROM moderation_actions ma JOIN reports r ON r.id = ma.report_id "
                        + "WHERE r.target_post_id = ? AND ma.action = 'RESOLVE_HIDE' "
                        + "ORDER BY ma.created_at DESC, ma.id DESC LIMIT 1",
                String.class, postId);
        return new HiddenPostNotice(postId, reasons.isEmpty() ? null : reasons.get(0));
    }
}
