package by.whatsappka.platform.jobs;

/** Обработчик одного типа задания. Регистрируется как бин; повторная обработка должна быть идемпотентной. */
public interface JobHandler {

    String type();

    void handle(JobContext context) throws Exception;
}
