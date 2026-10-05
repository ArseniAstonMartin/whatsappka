import type { ChatMessage } from './chat.service';

/** Старые ответы HTTP и повторы событий не должны откатывать правку или воскрешать удалённое сообщение. */
export function mergeMessages(current: ChatMessage[], fresh: ChatMessage[]): ChatMessage[] {
  const byId = new Map(current.map((message) => [message.id, message]));
  for (const message of fresh) {
    const known = byId.get(message.id);
    if (!known || message.version > known.version) {
      byId.set(message.id, message);
    }
  }
  return [...byId.values()].sort((a, b) => a.seq - b.seq);
}
