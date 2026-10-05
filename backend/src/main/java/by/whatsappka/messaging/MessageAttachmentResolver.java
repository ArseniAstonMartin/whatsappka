package by.whatsappka.messaging;

import by.whatsappka.media.access.MediaLinkResolver;
import by.whatsappka.media.access.MediaLinkType;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Вложение сообщения видно действующему участнику, для которого сообщение отправлено и не удалено. */
@Component
@ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class MessageAttachmentResolver implements MediaLinkResolver {

    private final JdbcTemplate jdbc;

    public MessageAttachmentResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MediaLinkType type() {
        return MediaLinkType.CHAT_ATTACHMENT;
    }

    @Override
    public boolean canView(UUID viewerId, UUID messageId) {
        Boolean visible = jdbc.queryForObject(MessageSql.CAN_VIEW_ATTACHMENT, Boolean.class, viewerId, messageId);
        return Boolean.TRUE.equals(visible);
    }
}
