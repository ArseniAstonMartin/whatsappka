package by.whatsappka.media;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Проверяет тело загрузки на лету: размер строго равен объявленному и в текстовом файле нет управляющих байтов.
 * Нарушение бросает {@link MediaRejectedException}, чтобы загрузка прервалась до фиксации объекта.
 */
final class ValidatingStream extends FilterInputStream {

    private final long declared;
    private final boolean rawText;
    private long count;

    ValidatingStream(InputStream in, long declared, boolean rawText) {
        super(in);
        this.declared = declared;
        this.rawText = rawText;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value == -1) {
            verifyEnd();
            return -1;
        }
        account(value);
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = super.read(buffer, offset, length);
        if (read == -1) {
            verifyEnd();
            return -1;
        }
        for (int i = offset; i < offset + read; i++) {
            account(buffer[i] & 0xFF);
        }
        return read;
    }

    private void account(int value) {
        count++;
        if (count > declared) {
            throw new MediaRejectedException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "size_mismatch", "Размер тела не совпадает с заголовком");
        }
        if (rawText && value == 0x00
                || rawText && value < 0x20 && value != '\t' && value != '\n' && value != '\r' && value != '\f') {
            throw new MediaRejectedException(
                    org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type",
                    "Текстовый файл содержит двоичные данные");
        }
    }

    private void verifyEnd() {
        if (count != declared) {
            throw new MediaRejectedException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "size_mismatch", "Размер тела не совпадает с заголовком");
        }
    }
}
