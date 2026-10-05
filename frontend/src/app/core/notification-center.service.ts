import { Injectable, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, filter } from 'rxjs';
import { ConnectionState } from './connection-state';
import { NotificationService, NotificationType } from './notification.service';
import { RealtimeEvent, RealtimeService } from './realtime/realtime.service';
import { ToastService } from '../shared/ui/toast/toast.service';

/** Текст тоста без содержимого: только вид события. Текст переписки и имя объекта в уведомлении не показываются. */
const TOAST_TEXT: Record<NotificationType, string> = {
  FOLLOW: 'На вас подписались',
  MESSAGE: 'Новое сообщение',
  CHAT_INVITATION: 'Приглашение в чат',
  COMMUNITY_INVITATION: 'Приглашение в сообщество',
  JOIN_REQUEST: 'Новая заявка в сообщество',
  JOIN_RESULT: 'Ответ на заявку в сообщество',
  COMMENT: 'Новый комментарий',
  REPLY: 'Новый ответ на комментарий',
  REACTION: 'Новая реакция',
  SYSTEM: 'Системное уведомление',
};

/**
 * Счётчик и синхронизация центра уведомлений. Счётчик всегда берётся с сервера: повтор события не увеличивает его.
 * Событие лишь говорит, что нужно перечитать. После восстановления связи перечитывается всё через REST.
 */
@Injectable({ providedIn: 'root' })
export class NotificationCenter {
  private readonly api = inject(NotificationService);
  private readonly realtime = inject(RealtimeService);
  private readonly connection = inject(ConnectionState);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  readonly unread = signal(0);
  /** Сигнал для открытого центра: нужно перечитать первую страницу. */
  readonly changes$ = new Subject<void>();

  private lastStatus = this.connection.status();

  constructor() {
    this.realtime.events$
      .pipe(filter((event: RealtimeEvent) => event.type === 'notification.created'))
      .subscribe((event) => this.onCreated(event));

    effect(() => {
      const status = this.connection.status();
      if (status === 'online' && this.lastStatus !== 'online') {
        void this.refresh();
      }
      this.lastStatus = status;
    });

    void this.refresh();
  }

  /** Перечитать счётчик с сервера и сообщить открытому центру. */
  async refresh(): Promise<void> {
    try {
      const { unread } = await this.api.unreadCount();
      this.unread.set(unread);
      this.changes$.next();
    } catch {
      // Без связи счётчик остаётся прежним; после восстановления связи он перечитается.
    }
  }

  private onCreated(event: RealtimeEvent): void {
    void this.refresh();
    const payload = (event.payload ?? {}) as { type?: NotificationType; targetId?: string };
    if (payload.type === 'MESSAGE' && payload.targetId && this.router.url.startsWith(`/chats/${payload.targetId}`)) {
      return;
    }
    const text = payload.type && TOAST_TEXT[payload.type] ? TOAST_TEXT[payload.type] : 'Новое уведомление';
    this.toasts.show(text, 'info');
  }
}
