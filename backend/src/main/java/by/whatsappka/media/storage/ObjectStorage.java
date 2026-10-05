package by.whatsappka.media.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Хранилище объектов. Ключи задаёт вызывающий код; наружу адаптер ключи и учётные данные не отдаёт.
 */
public interface ObjectStorage {

    /** Потоковая запись: тело читается из потока, в память целиком не загружается. */
    void put(String key, InputStream content, long size, String contentType);

    /** Поток объекта; закрывает вызывающий код. Бросает {@link ObjectMissingException}, если объекта нет. */
    InputStream get(String key);

    Optional<ObjectInfo> head(String key);

    /** Повторное удаление безопасно: отсутствие объекта не ошибка. */
    void delete(String key);

    record ObjectInfo(long size, String contentType) {
    }
}
