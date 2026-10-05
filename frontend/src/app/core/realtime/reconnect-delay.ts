const MIN_MS = 1_000;
const MAX_MS = 30_000;

/**
 * Задержка переподключения в диапазоне 1–30 с с джиттером: попытка n ждёт случайное время
 * между 1 с и экспоненциальным пределом. Так клиенты не переподключаются одновременно.
 */
export function reconnectDelayMs(attempt: number, random: () => number = Math.random): number {
  const ceiling = Math.min(MAX_MS, MIN_MS * 2 ** Math.min(Math.max(attempt, 0), 10));
  return Math.round(MIN_MS + random() * (ceiling - MIN_MS));
}
