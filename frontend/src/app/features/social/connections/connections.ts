import { Component, OnInit, inject, input, signal } from '@angular/core';
import { ProfileService, UserSummary, CursorPage } from '../../../core/profile.service';
import { toProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { RouterLink } from '@angular/router';

/**
 * Подписчики или подписки пользователя, страницами. «Загрузить ещё» дописывает страницу в конец списка.
 */
@Component({
  selector: 'app-connections',
  imports: [AppButton, Skeleton, StatePanel, RouterLink],
  templateUrl: './connections.html',
  styleUrl: './connections.scss',
})
export class Connections implements OnInit {
  private readonly profiles = inject(ProfileService);

  readonly username = input.required<string>();
  readonly list = input.required<string>();

  protected readonly title = signal('');
  protected readonly items = signal<UserSummary[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notFound = signal(false);

  private userId: string | null = null;
  private loadingToken = 0;

  ngOnInit(): void {
    void this.start();
  }

  protected isFollowers(): boolean {
    return this.list() === 'followers';
  }

  protected async start(): Promise<void> {
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    try {
      const profile = await this.profiles.publicByUsername(this.username());
      this.userId = profile.id;
      this.title.set(this.isFollowers() ? `Подписчики ${profile.displayName}` : `Подписки ${profile.displayName}`);
      await this.fetchPage(null, true);
    } catch (error) {
      const problem = toProblem(error);
      if (problem.status === 404) {
        this.notFound.set(true);
      } else {
        this.error.set(problem.message);
      }
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
      await this.fetchPage(this.nextCursor(), false);
    } catch (error) {
      this.error.set(toProblem(error).message);
    } finally {
      this.loadingMore.set(false);
    }
  }

  private async fetchPage(cursor: string | null, replace: boolean): Promise<void> {
    if (!this.userId) {
      return;
    }
    const token = ++this.loadingToken;
    const page: CursorPage<UserSummary> = this.isFollowers()
      ? await this.profiles.followers(this.userId, cursor)
      : await this.profiles.following(this.userId, cursor);
    if (token !== this.loadingToken) {
      return;
    }
    // Страницы дописываются, без дублей: пользователь с уже показанным id в конец не добавляется.
    const seen = new Set(replace ? [] : this.items().map((u) => u.id));
    const fresh = page.items.filter((u) => !seen.has(u.id));
    this.items.set(replace ? fresh : [...this.items(), ...fresh]);
    this.nextCursor.set(page.nextCursor);
    this.hasMore.set(page.hasMore);
  }
}
