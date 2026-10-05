export interface DedupableEvent {
  eventId: string;
  entityId?: string | null;
  entityVersion?: number | null;
}

/**
 * Убирает повторы at-least-once: по eventId и по версии сущности.
 * Событие с версией не выше уже принятой для той же сущности — устаревший повтор.
 */
export class EventDeduplicator {
  private readonly seenIds = new Set<string>();
  private readonly seenOrder: string[] = [];
  private readonly versions = new Map<string, number>();

  constructor(private readonly capacity = 500) {}

  /** true — событие новое и его нужно обработать. */
  accept(event: DedupableEvent): boolean {
    if (this.seenIds.has(event.eventId)) {
      return false;
    }
    if (event.entityId && typeof event.entityVersion === 'number') {
      const last = this.versions.get(event.entityId);
      if (last !== undefined && event.entityVersion <= last) {
        return false;
      }
      this.versions.delete(event.entityId);
      this.versions.set(event.entityId, event.entityVersion);
      if (this.versions.size > this.capacity * 4) {
        const oldest = this.versions.keys().next().value;
        if (oldest !== undefined) {
          this.versions.delete(oldest);
        }
      }
    }
    this.seenIds.add(event.eventId);
    this.seenOrder.push(event.eventId);
    if (this.seenOrder.length > this.capacity) {
      const evicted = this.seenOrder.shift();
      if (evicted !== undefined) {
        this.seenIds.delete(evicted);
      }
    }
    return true;
  }
}
