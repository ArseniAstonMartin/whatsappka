import { Component, DestroyRef, OnInit, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { SearchService, UserHit, PostHit, GroupHit, HashtagHit } from '../../../core/search.service';
import { CursorPage } from '../../../core/profile.service';
import { toProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';

export type SearchTab = 'people' | 'posts' | 'groups' | 'tags';

const TABS: readonly { id: SearchTab; label: string }[] = [
  { id: 'people', label: 'Люди' },
  { id: 'posts', label: 'Посты' },
  { id: 'groups', label: 'Сообщества' },
  { id: 'tags', label: 'Хештеги' },
];

const DEBOUNCE_MS = 300;
const MIN_QUERY = 2;

/** Единый поиск: вкладки, debounce, отбрасывание устаревших ответов. Запрос и вкладка живут в адресе. */
@Component({
  selector: 'app-search-page',
  imports: [AppButton, Skeleton, StatePanel, RouterLink],
  templateUrl: './search-page.html',
  styleUrl: './search-page.scss',
})
export class SearchPage implements OnInit {
  private readonly search = inject(SearchService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly tabs = TABS;
  protected readonly input = signal('');
  protected readonly query = signal('');
  protected readonly tab = signal<SearchTab>('people');

  protected readonly people = signal<UserHit[]>([]);
  protected readonly posts = signal<PostHit[]>([]);
  protected readonly groups = signal<GroupHit[]>([]);
  protected readonly tags = signal<HashtagHit[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(false);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Выполненный запрос; пока его нет, показываем подсказку, а не пустой результат. */
  protected readonly searched = signal(false);

  protected readonly tooShort = computed(() => this.query().trim().length < MIN_QUERY);
  protected readonly count = computed(() => {
    switch (this.tab()) {
      case 'people': return this.people().length;
      case 'posts': return this.posts().length;
      case 'groups': return this.groups().length;
      case 'tags': return this.tags().length;
    }
  });

  private readonly typed = new Subject<string>();
  /** Номер текущего запроса: ответ старого запроса его не перезаписывает. */
  private token = 0;

  constructor() {
    this.typed
      .pipe(debounceTime(DEBOUNCE_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.commit(value.trim()));
  }

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const tab = params.get('tab');
    this.tab.set(this.isTab(tab) ? tab : 'people');
    const q = (params.get('q') ?? '').trim();
    this.input.set(q);
    this.query.set(q);
    this.run();
  }

  protected onInput(value: string): void {
    this.input.set(value);
    this.typed.next(value);
  }

  protected selectTab(tab: SearchTab): void {
    if (tab === this.tab()) {
      return;
    }
    this.tab.set(tab);
    this.persist();
    this.run();
  }

  protected async loadMore(): Promise<void> {
    const cursor = this.nextCursor();
    if (this.loadingMore() || !this.hasMore() || !cursor || this.tooShort()) {
      return;
    }
    const token = this.token;
    this.loadingMore.set(true);
    try {
      if (this.tab() === 'people') {
        this.applyPage(await this.search.users(this.query(), cursor), token, (rows) => this.people.set([...this.people(), ...rows]));
      } else if (this.tab() === 'posts') {
        this.applyPage(await this.search.posts(this.query(), cursor), token, (rows) => this.posts.set([...this.posts(), ...rows]));
      } else if (this.tab() === 'groups') {
        this.applyPage(await this.search.groups(this.query(), cursor), token, (rows) => this.groups.set([...this.groups(), ...rows]));
      }
    } catch (error) {
      if (token === this.token) {
        this.error.set(toProblem(error).message);
      }
    } finally {
      if (token === this.token) {
        this.loadingMore.set(false);
      }
    }
  }

  protected retry(): void {
    this.run();
  }

  protected tabId(tab: SearchTab): string {
    return `search-tab-${tab}`;
  }

  protected tagLink(name: string): string[] {
    return ['/hashtags', name];
  }

  protected time(iso: string): string {
    return new Date(iso).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  /** Новый запрос из строки ввода: адрес обновляется без новой записи в истории. */
  private commit(value: string): void {
    if (value === this.query()) {
      return;
    }
    this.query.set(value);
    this.persist();
    this.run();
  }

  private persist(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { q: this.query() || null, tab: this.tab() },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  /**
   * Выполняет поиск по текущей вкладке. Пустой или короткий запрос не отправляет ничего.
   * Ответ применяется только если за время ожидания не начался другой запрос.
   */
  private run(): void {
    const token = ++this.token;
    this.error.set(null);
    this.clearResults();
    if (this.tooShort()) {
      this.searched.set(false);
      this.loading.set(false);
      return;
    }
    this.searched.set(true);
    this.loading.set(true);
    const q = this.query();
    const tab = this.tab();
    const request = (): Promise<unknown> => {
      switch (tab) {
        case 'people': return this.search.users(q, null).then((page) => this.applyPage(page, token, (rows) => this.people.set(rows)));
        case 'posts': return this.search.posts(q, null).then((page) => this.applyPage(page, token, (rows) => this.posts.set(rows)));
        case 'groups': return this.search.groups(q, null).then((page) => this.applyPage(page, token, (rows) => this.groups.set(rows)));
        case 'tags': return this.search.hashtags(q).then((rows) => {
          if (token === this.token) {
            this.tags.set(rows);
          }
        });
      }
    };
    request()
      .catch((error: unknown) => {
        if (token === this.token) {
          this.error.set(toProblem(error).message);
        }
      })
      .finally(() => {
        if (token === this.token) {
          this.loading.set(false);
        }
      });
  }

  private applyPage<T>(page: CursorPage<T>, token: number, store: (rows: T[]) => void): void {
    if (token !== this.token) {
      return;
    }
    store(page.items);
    this.nextCursor.set(page.nextCursor);
    this.hasMore.set(page.hasMore);
  }

  private clearResults(): void {
    this.people.set([]);
    this.posts.set([]);
    this.groups.set([]);
    this.tags.set([]);
    this.nextCursor.set(null);
    this.hasMore.set(false);
  }

  private isTab(value: string | null): value is SearchTab {
    return value === 'people' || value === 'posts' || value === 'groups' || value === 'tags';
  }
}
