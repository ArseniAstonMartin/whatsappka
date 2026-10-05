import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PostService, PostSummary } from '../../../core/post.service';
import { toProblem } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Card } from '../../../shared/ui/card/card';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';

const STATUS_LABEL: Record<string, string> = { DRAFT: 'Черновик', SCHEDULED: 'Отложено' };

const SCHEDULE_FAILURE_LABEL: Record<string, string> = {
  empty_post: 'Нужен текст или хотя бы одно готовое изображение',
  not_group_member: 'Вы больше не состоите в группе этой записи',
  author_inactive: 'Аккаунт был неактивен в момент публикации',
};

/** Свои черновики и отложенные записи (TASK-046). Причина отказа worker видна прямо в списке. */
@Component({
  selector: 'app-post-drafts',
  imports: [RouterLink, DatePipe, AppButton, Card, Skeleton, StatePanel],
  templateUrl: './post-drafts.html',
  styleUrl: './post-drafts.scss',
})
export class PostDrafts implements OnInit {
  private readonly posts = inject(PostService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  protected readonly items = signal<PostSummary[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly removingIds = signal<ReadonlySet<string>>(new Set());

  ngOnInit(): void {
    void this.start();
  }

  protected statusLabel(status: string): string {
    return STATUS_LABEL[status] ?? status;
  }

  protected failureLabel(reason: string | null): string | null {
    return reason ? (SCHEDULE_FAILURE_LABEL[reason] ?? reason) : null;
  }

  protected excerpt(body: string | null): string {
    if (!body) {
      return '(без текста)';
    }
    return body.length > 120 ? body.slice(0, 120) + '…' : body;
  }

  protected async start(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const page = await this.posts.drafts(null);
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
      const page = await this.posts.drafts(this.nextCursor());
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

  protected isRemoving(id: string): boolean {
    return this.removingIds().has(id);
  }

  protected async remove(post: PostSummary): Promise<void> {
    if (this.isRemoving(post.id)) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: 'Удалить запись?',
      message: 'Действие нельзя отменить из интерфейса.',
      confirmLabel: 'Удалить',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.removingIds.update((ids) => new Set([...ids, post.id]));
    try {
      await this.posts.remove(post.id);
      this.items.update((list) => list.filter((p) => p.id !== post.id));
      this.toasts.show('Запись удалена', 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.removingIds.update((ids) => {
        const next = new Set(ids);
        next.delete(post.id);
        return next;
      });
    }
  }
}
