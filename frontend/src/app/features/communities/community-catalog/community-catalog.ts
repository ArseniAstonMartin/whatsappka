import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommunityService, CommunitySummary } from '../../../core/community.service';
import { toProblem } from '../../../core/api-error';
import { Avatar } from '../../../shared/ui/avatar/avatar';
import { MediaView } from '../../../shared/media/media-view/media-view';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';

type Tab = 'catalog' | 'mine';

/** Каталог открытых сообществ и список своих групп, двумя вкладками одной страницы. */
@Component({
  selector: 'app-community-catalog',
  imports: [RouterLink, AppButton, Avatar, MediaView, Skeleton, StatePanel],
  templateUrl: './community-catalog.html',
  styleUrl: './community-catalog.scss',
})
export class CommunityCatalog implements OnInit {
  private readonly communities = inject(CommunityService);

  protected readonly tab = signal<Tab>('catalog');
  protected readonly items = signal<CommunitySummary[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly mineItems = signal<CommunitySummary[] | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.start();
  }

  protected async selectTab(tab: Tab): Promise<void> {
    if (this.tab() === tab) {
      return;
    }
    this.tab.set(tab);
    this.error.set(null);
    if (tab === 'mine' && this.mineItems() === null) {
      await this.loadMine();
    }
  }

  protected async start(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.fetchCatalogPage(null);
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
      await this.fetchCatalogPage(this.nextCursor());
    } catch (error) {
      this.error.set(toProblem(error).message);
    } finally {
      this.loadingMore.set(false);
    }
  }

  protected visibilityLabel(visibility: string): string {
    return visibility === 'PUBLIC' ? 'Открытое' : 'Приватное';
  }

  private async fetchCatalogPage(cursor: string | null): Promise<void> {
    const page = await this.communities.catalog(cursor);
    this.items.set(cursor ? [...this.items(), ...page.items] : page.items);
    this.nextCursor.set(page.nextCursor);
    this.hasMore.set(page.hasMore);
  }

  private async loadMine(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.mineItems.set(await this.communities.mine());
    } catch (error) {
      this.error.set(toProblem(error).message);
    } finally {
      this.loading.set(false);
    }
  }
}
