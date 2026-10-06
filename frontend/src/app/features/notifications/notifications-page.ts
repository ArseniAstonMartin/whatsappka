import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { NotificationCenter } from '../../core/notification-center.service';
import { NotificationItem, NotificationLink, NotificationService, NotificationType } from '../../core/notification.service';
import { toProblem } from '../../core/api-error';
import { AppButton } from '../../shared/ui/button/app-button';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../shared/state-panel/state-panel';
import { ToastService } from '../../shared/ui/toast/toast.service';

const LABELS: Record<NotificationType, string> = {
  FOLLOW: 'подписался(-ась) на вас',
  MESSAGE: 'отправил(а) сообщение',
  CHAT_INVITATION: 'пригласил(а) вас в чат',
  COMMUNITY_INVITATION: 'пригласил(а) вас в сообщество',
  JOIN_REQUEST: 'подал(а) заявку в сообщество',
  JOIN_RESULT: 'решение по вашей заявке в сообщество',
  COMMENT: 'прокомментировал(а) вашу запись',
  REPLY: 'ответил(а) на ваш комментарий',
  REACTION: 'отреагировал(а) на ваш материал',
  SYSTEM: 'системное уведомление',
  MODERATION_RESULT: 'решение по вашей жалобе',
  CONTENT_HIDDEN: 'скрыл(а) ваш материал модерацией',
};

/**
 * Центр уведомлений: собственные уведомления, непрочитанные, прочтение одного и всех. Переход ведёт только
 * к объекту, который сервер вернул в ссылке; без неё показываем нейтральное сообщение.
 */
@Component({
  selector: 'app-notifications-page',
  imports: [AppButton, Skeleton, StatePanel],
  templateUrl: './notifications-page.html',
  styleUrl: './notifications-page.scss',
})
export class NotificationsPage implements OnInit {
  private readonly api = inject(NotificationService);
  private readonly center = inject(NotificationCenter);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly items = signal<NotificationItem[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly bulkBusy = signal(false);

  private loadToken = 0;

  ngOnInit(): void {
    void this.start();
    this.center.changes$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.reloadFirst());
  }

  protected label(item: NotificationItem): string {
    return LABELS[item.type] ?? 'уведомление';
  }

  /** У системного уведомления нет актора по природе; пустой актор у остальных означает скрытие из-за блокировки. */
  protected who(item: NotificationItem): string {
    if (item.actor) {
      return item.actor.displayName;
    }
    return item.type === 'SYSTEM' ? 'Система' : 'Пользователь недоступен';
  }

  protected time(iso: string): string {
    return new Date(iso).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  protected async start(): Promise<void> {
    const token = ++this.loadToken;
    this.loading.set(true);
    this.error.set(null);
    try {
      const page = await this.api.list(null);
      if (token !== this.loadToken) {
        return;
      }
      this.items.set(page.items);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
    } catch (error) {
      if (token === this.loadToken) {
        this.error.set(toProblem(error).message);
      }
    } finally {
      if (token === this.loadToken) {
        this.loading.set(false);
      }
    }
  }

  /** Перечитывает первую страницу и сохраняет уже загруженные строки ниже без дублей. */
  private async reloadFirst(): Promise<void> {
    if (this.loading()) {
      return;
    }
    try {
      const page = await this.api.list(null);
      const seen = new Set(page.items.map((item) => item.id));
      const rest = this.items().filter((item) => !seen.has(item.id));
      this.items.set([...page.items, ...rest]);
    } catch {
      // Ошибку фонового обновления не показываем: следующее событие или переход на страницу повторит попытку.
    }
  }

  protected async loadMore(): Promise<void> {
    if (this.loadingMore() || !this.hasMore()) {
      return;
    }
    this.loadingMore.set(true);
    try {
      const page = await this.api.list(this.nextCursor());
      const seen = new Set(this.items().map((item) => item.id));
      this.items.set([...this.items(), ...page.items.filter((item) => !seen.has(item.id))]);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.loadingMore.set(false);
    }
  }

  protected async open(item: NotificationItem): Promise<void> {
    if (!item.read) {
      await this.markOne(item);
    }
    const route = this.routeFor(item.link);
    if (!route) {
      this.notice.set('Объект недоступен: он удалён, скрыт или вам больше не доступен.');
      return;
    }
    this.notice.set(null);
    await this.router.navigate(route);
  }

  protected async markAll(): Promise<void> {
    if (this.bulkBusy()) {
      return;
    }
    this.bulkBusy.set(true);
    try {
      await this.api.markAllRead();
      this.items.set(this.items().map((item) => ({ ...item, read: true })));
      await this.center.refresh();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.bulkBusy.set(false);
    }
  }

  private async markOne(item: NotificationItem): Promise<void> {
    try {
      await this.api.markRead(item.id);
      this.items.set(this.items().map((row) => (row.id === item.id ? { ...row, read: true } : row)));
      await this.center.refresh();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }

  private routeFor(link: NotificationLink | null): string[] | null {
    if (!link) {
      return null;
    }
    switch (link.kind) {
      case 'CHAT':
        return link.id ? ['/chats', link.id] : null;
      case 'POST':
        return link.id ? ['/posts', link.id] : null;
      case 'GROUP':
        return link.slug ? ['/groups', link.slug] : null;
      case 'USER':
        return link.slug ? ['/users', link.slug] : null;
    }
  }
}
