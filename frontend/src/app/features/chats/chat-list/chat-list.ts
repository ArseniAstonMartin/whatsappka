import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Subject, debounceTime, filter } from 'rxjs';
import { ChatService, Conversation } from '../../../core/chat.service';
import { toProblem } from '../../../core/api-error';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Avatar } from '../../../shared/ui/avatar/avatar';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { MediaView } from '../../../shared/media/media-view/media-view';

/** События, после которых список перечитывается: новое сообщение, прочтение в другом устройстве, состав чата. */
const REFRESH_EVENTS = ['message.created', 'conversation.read', 'conversation.membership.changed'];

/**
 * Личные диалоги и групповые чаты пользователя страницами (TASK-062). Фильтр работает только по уже
 * загруженным чатам и по названию/имени собеседника: это не поиск по сообщениям, подсказка говорит об этом прямо.
 */
@Component({
  selector: 'app-chat-list',
  imports: [AppButton, Avatar, MediaView, Skeleton, StatePanel, RouterLink, RouterLinkActive],
  templateUrl: './chat-list.html',
  styleUrl: './chat-list.scss',
})
export class ChatList implements OnInit {
  private readonly chats = inject(ChatService);
  private readonly router = inject(Router);

  protected readonly items = signal<Conversation[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');

  protected readonly visible = computed(() => {
    const needle = this.query().trim().toLowerCase();
    if (!needle) {
      return this.items();
    }
    return this.items().filter((chat) => this.displayName(chat).toLowerCase().includes(needle));
  });

  private loadingToken = 0;
  /** Счётчики непрочитанного и последние сообщения обновляются по событиям, не дожидаясь перехода на страницу. */
  private readonly refreshRequests = new Subject<void>();
  private readonly realtime = inject(RealtimeService);

  constructor() {
    this.realtime.events$
      .pipe(
        filter((event) => REFRESH_EVENTS.includes(event.type)),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.refreshRequests.next());
    this.refreshRequests
      .pipe(debounceTime(300), takeUntilDestroyed())
      .subscribe(() => void this.refreshQuietly());

    /** Возврат к списку (например, после выхода из группового чата) перечитывает его — локального кеша нет. */
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd && event.urlAfterRedirects === '/chats'),
        takeUntilDestroyed(),
      )
      .subscribe(() => void this.start());
  }

  ngOnInit(): void {
    void this.start();
  }

  /** Тихое обновление первой страницы: уже загруженные страницы и порядок сохраняются, меняются счётчики и превью. */
  protected async refreshQuietly(): Promise<void> {
    try {
      const page = await this.chats.list(null);
      const fresh = new Map(page.items.map((chat) => [chat.id, chat]));
      this.items.update((list) => {
        const known = new Set(list.map((chat) => chat.id));
        const added = page.items.filter((chat) => !known.has(chat.id));
        return [...added, ...list.map((chat) => fresh.get(chat.id) ?? chat)];
      });
    } catch {
      // Счётчики остаются прежними до следующего события или перехода на страницу.
    }
  }

  protected async start(): Promise<void> {
    const token = ++this.loadingToken;
    this.loading.set(true);
    this.error.set(null);
    try {
      const page = await this.chats.list(null);
      if (token !== this.loadingToken) {
        return;
      }
      this.items.set(page.items);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
    } catch (error) {
      if (token === this.loadingToken) {
        this.error.set(toProblem(error).message);
      }
    } finally {
      if (token === this.loadingToken) {
        this.loading.set(false);
      }
    }
  }

  /** Страница дописывается в конец: уже показанные строки остаются на месте, дубли отбрасываются. */
  protected async loadMore(): Promise<void> {
    if (this.loadingMore() || !this.hasMore()) {
      return;
    }
    this.loadingMore.set(true);
    try {
      const page = await this.chats.list(this.nextCursor());
      const seen = new Set(this.items().map((chat) => chat.id));
      this.items.set([...this.items(), ...page.items.filter((chat) => !seen.has(chat.id))]);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
    } catch (error) {
      this.error.set(toProblem(error).message);
    } finally {
      this.loadingMore.set(false);
    }
  }

  protected displayName(chat: Conversation): string {
    return chat.type === 'GROUP' ? (chat.title ?? 'Групповой чат') : (chat.otherDisplayName ?? '');
  }

  /** У личного диалога это аватар собеседника, у группового — аватар чата. */
  protected avatarId(chat: Conversation): string | null {
    return chat.avatarMediaId;
  }

  protected preview(chat: Conversation): string {
    const last = chat.lastMessage;
    if (!last) {
      return 'Сообщений пока нет';
    }
    return last.body ?? 'Вложение';
  }

  protected unreadLabel(count: number): string {
    return count > 99 ? '99+' : String(count);
  }
}
