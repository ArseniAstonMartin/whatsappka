package by.whatsappka.media.storage;

/** Объекта нет в bucket. Текст без ключа, чтобы путь не попал в журнал или ответ. */
public class ObjectMissingException extends RuntimeException {

    public ObjectMissingException() {
        super("Объект не найден в хранилище");
    }
}
