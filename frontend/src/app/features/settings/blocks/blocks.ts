import { Component, OnInit, inject, signal } from '@angular/core';
import { ProfileService, UserSummary } from '../../../core/profile.service';
import { toProblem } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';

/**
 * Свои блокировки. Разблокировка выполняется после ответа сервера: до этого строка остаётся в списке.
 */
@Component({
  selector: 'app-blocks-settings',
  imports: [AppButton, StatePanel, Skeleton],
  templateUrl: './blocks.html',
  styleUrl: './blocks.scss',
})
export class BlocksSettings implements OnInit {
  private readonly profiles = inject(ProfileService);
  private readonly toasts = inject(ToastService);

  protected readonly items = signal<UserSummary[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly pendingIds = signal<ReadonlySet<string>>(new Set());

  ngOnInit(): void {
    void this.start();
  }

  protected async start(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const page = await this.profiles.myBlocks(null);
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
      const page = await this.profiles.myBlocks(this.nextCursor());
      const seen = new Set(this.items().map((u) => u.id));
      this.items.set([...this.items(), ...page.items.filter((u) => !seen.has(u.id))]);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.loadingMore.set(false);
    }
  }

  protected isPending(id: string): boolean {
    return this.pendingIds().has(id);
  }

  protected async unblock(user: UserSummary): Promise<void> {
    if (this.isPending(user.id)) {
      return;
    }
    this.pendingIds.update((ids) => new Set([...ids, user.id]));
    try {
      await this.profiles.unblock(user.id);
      this.items.update((list) => list.filter((u) => u.id !== user.id));
      this.toasts.show(`${user.displayName} разблокирован`, 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.pendingIds.update((ids) => {
        const next = new Set(ids);
        next.delete(user.id);
        return next;
      });
    }
  }
}
