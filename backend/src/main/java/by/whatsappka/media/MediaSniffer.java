package by.whatsappka.media;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Определяет тип по первым байтам. Текст принимается только если в начале нет двоичных управляющих байтов,
 * остальная часть текстового файла проверяется потоково в {@link ValidatingStream}.
 */
public final class MediaSniffer {

    /** Сколько байт читается для определения типа. */
    public static final int HEAD_BYTES = 4096;

    private MediaSniffer() {
    }

    public static Optional<MediaType> detect(byte[] head) {
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(MediaType.JPEG);
        }
        if (startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(MediaType.PNG);
        }
        if (head.length >= 12 && ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP")) {
            return Optional.of(MediaType.WEBP);
        }
        if (ascii(head, 0, "%PDF-")) {
            return Optional.of(MediaType.PDF);
        }
        if (looksLikeText(head) && !looksLikeMarkup(head)) {
            return Optional.of(MediaType.TXT);
        }
        return Optional.empty();
    }

    /** SVG, HTML и скрипты в первой версии не принимаются, даже если они состоят из обычного текста. */
    static boolean looksLikeMarkup(byte[] head) {
        String start = new String(head, StandardCharsets.ISO_8859_1).stripLeading().toLowerCase(java.util.Locale.ROOT);
        return start.startsWith("<svg") || start.startsWith("<html") || start.startsWith("<!doctype")
                || start.startsWith("<?xml") || start.startsWith("<script") || start.startsWith("<head")
                || start.startsWith("<body");
    }

    /** Управляющие байты, кроме табуляции и переводов строк, означают двоичный файл. */
    static boolean looksLikeText(byte[] head) {
        if (head.length == 0) {
            return false;
        }
        for (byte b : head) {
            int value = b & 0xFF;
            if (value == 0x00 || (value < 0x20 && value != '\t' && value != '\n' && value != '\r' && value != '\f')) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] head, int... magic) {
        if (head.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((head[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] head, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        if (head.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (head[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
