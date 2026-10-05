import { Component, OnChanges, OnInit, SimpleChanges, inject, input, signal } from '@angular/core';
import { CursorPage } from '../../core/profile.service';
import { PostSummaryPublic } from '../../core/post.service';
import { toProblem } from '../../core/api-error';
import { ToastService } from '../ui/toast/toast.service';
import { AppButton } from '../ui/button/app-button';
import { Skeleton } from '../ui/skeleton/skeleton';
import { StatePanel } from '../state-panel/state-panel';
import { PostCard } from '../post-card/post-card';

export type PostPageLoader = (cursor: string | null) => Promise<CursorPage<PostSummaryPublic>>;

/**
 * Курсорный список постов (TASK-047): лента, профиль, группа используют один компонент с общим
 * loading/empty/error и «Загрузить ещё». «Обновить» подтягивает новые записи сверху без дублей уже
 * показанных карточек.
 */
@Component({
  selector: 'app-post-list',
  imports: [AppButton, Skeleton, StatePanel, PostCard],
  templateUrl: './post-list.html',
  styleUrl: './post-list.scss',
})
export class PostList implements OnInit, OnChanges {
  private readonly toasts = inject(ToastService);

  readonly loader = input.required<PostPageLoader>();
  /** Угловой компонент может переиспользоваться при смене параметра маршрута (другой автор/группа):
   * ссылка на loader при этом не меняется, поэтому источник различают по этому ключу. */
  readonly listKey = input<string | null>(null);
  readonly emptyTitle = input('Публикаций пока нет');
  readonly emptyMessage = input('');

  protected readonly items = signal<PostSummaryPublic[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly refreshing = signal(false);
  protected readonly error = signal<string | null>(null);

  private started = false;

  ngOnInit(): void {
    this.started = true;
    void this.start();
  }

  /** listKey меняется (другой автор/группа на переиспользованном компоненте) — список перечитывается с начала. */
  ngOnChanges(changes: SimpleChanges): void {
    if (this.started && changes['listKey'] && !changes['listKey'].firstChange) {
      void this.start();
    }
  }

  protected async start(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const page = await this.loader()(null);
      this.items.set(page.items);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
    } catch (error) {
      this.error.set(toProblem(error).message);
    } finally {
      this.loading.set(false);
    }
  }

  protected async loadMore(): Promise<void> {
    if (this.loadingMore() || !this.hasMore()) {
      return;
    }
    this.loadingMore.set(true);
    try {
      const page = await this.loader()(this.nextCursor());
      const seen = new Set(this.items().map((p) => p.id));
      this.items.set([...this.items(), ...page.items.filter((p) => !seen.has(p.id))]);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.loadingMore.set(false);
    }
  }

  /** Подтягивает новые записи сверху; уже показанные карточки не дублируются. */
  protected async refreshTop(): Promise<void> {
    if (this.refreshing()) {
      return;
    }
    this.refreshing.set(true);
    try {
      const page = await this.loader()(null);
      const seen = new Set(this.items().map((p) => p.id));
      const fresh = page.items.filter((p) => !seen.has(p.id));
      if (fresh.length > 0) {
        this.items.set([...fresh, ...this.items()]);
      }
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.refreshing.set(false);
    }
  }

  protected onRemoved(id: string): void {
    this.items.update((list) => list.filter((p) => p.id !== id));
  }
}
