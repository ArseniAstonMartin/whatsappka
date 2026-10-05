package by.whatsappka.platform.ratelimit;

import java.time.Duration;

/** Счётчики окон. Реализация в Redis общая для всех экземпляров; локальная — запасная. */
public interface RateCounters {

    /** Увеличивает счётчик и возвращает новое значение. Окно начинается с первой попытки. */
    long increment(String key, Duration window);

    /** Текущее значение счётчика в окне; 0, если окна нет. */
    long current(String key);

    /** Сколько осталось до конца окна. */
    Duration remaining(String key);
}
