package by.whatsappka.media.storage;

/** Сбой хранилища (сеть, отказ MinIO). Детали SDK не раскрываются наружу. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
