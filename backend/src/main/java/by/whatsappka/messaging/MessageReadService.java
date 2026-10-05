package by.whatsappka.messaging;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Прочтение и счётчики. Прогресс чтения монотонен и ограничен существующими сообщениями чата. */
@Service
public class MessageReadService {

    public record ReadState(long lastReadSeq, long unread) {
    }

    public record ReadStatus(long readBy, long eligible) {
    }

    private final JdbcTemplate jdbc;

    public MessageReadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ReadState markRead(UUID viewer, UUID conversationId, long seq) {
        if (seq < 0) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("seq", "Номер сообщения не может быть отрицательным")));
        }
        List<Long> updated = jdbc.queryForList(MessageSql.READ_UPDATE, Long.class, seq, conversationId, viewer);
        if (updated.isEmpty()) {
            throw ApiException.notFound();
        }
        return unread(viewer, conversationId);
    }

    @Transactional(readOnly = true)
    public ReadState unread(UUID viewer, UUID conversationId) {
        List<ReadState> rows = jdbc.query(MessageSql.UNREAD_FOR_MEMBER, (rs, n) -> new ReadState(
                rs.getLong("last_read_seq"), rs.getLong("unread")), conversationId, viewer);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public ReadStatus readStatus(UUID viewer, UUID conversationId, UUID messageId) {
        requireActive(viewer, conversationId);
        List<ReadStatus> rows = jdbc.query(MessageSql.READ_STATUS, (rs, n) -> new ReadStatus(
                rs.getLong("read_by"), rs.getLong("eligible")), messageId, conversationId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        return rows.get(0);
    }

    private void requireActive(UUID viewer, UUID conversationId) {
        Boolean member = jdbc.queryForObject(ConversationSql.IS_ACTIVE_MEMBER, Boolean.class, conversationId, viewer);
        if (!Boolean.TRUE.equals(member)) {
            throw ApiException.notFound();
        }
    }
}
