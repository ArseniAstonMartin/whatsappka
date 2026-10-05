package by.whatsappka.messaging;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.FieldErrorDetail;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Правила содержимого сообщения (FR-06) и отпечатка повтора. */
public final class MessageRules {

    public static final int BODY_MAX = 4000;
    public static final int ATTACHMENTS_MAX = 5;

    private MessageRules() {
    }

    /** Текст до 4000 символов и/или до 5 вложений; пустое сообщение не отправляется. */
    public static void validate(String body, List<UUID> media) {
        int length = body == null ? 0 : body.length();
        if (length > BODY_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("body", "Не больше 4000 символов")));
        }
        if (media.size() > ATTACHMENTS_MAX) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("attachments", "Не больше 5 вложений")));
        }
        if ((body == null || body.isBlank()) && media.isEmpty()) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("body", "Сообщение пустое")));
        }
        if (media.stream().distinct().count() != media.size()) {
            throw ApiException.validation("Проверьте поля запроса",
                    List.of(new FieldErrorDetail("attachments", "Вложения не должны повторяться")));
        }
    }

    /** Отпечаток содержимого: текст и порядок вложений. Одинаковый повтор даёт одинаковый отпечаток. */
    public static String fingerprint(String body, List<UUID> media) {
        StringBuilder canonical = new StringBuilder(body == null ? "" : body).append('\u001f');
        for (UUID id : media) {
            canonical.append(id).append(',');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    /** Повтор с тем же client_message_id: тот же отпечаток возвращает сохранённое, иной — конфликт. */
    public static void requireSameContent(String stored, String incoming) {
        if (!stored.equals(incoming)) {
            throw ApiException.conflict("Этот client_message_id уже использован для другого сообщения");
        }
    }
}
