import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { CurrentUser } from '../../core/current-user';
import { toProblem } from '../../core/api-error';
import {
  ACTION_LABELS, Evidence, JournalEntry, KIND_LABELS, ModerationService, ModerationStatus,
  ModerationTargetKind, QueueItem, REASON_LABELS, authorIdOf,
} from '../../core/moderation.service';
import { AppButton } from '../../shared/ui/button/app-button';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../shared/state-panel/state-panel';
import { ToastService } from '../../shared/ui/toast/toast.service';

const STATUS_LABELS: Record<ModerationStatus, string> = {
  OPEN: 'Открыта', IN_REVIEW: 'В работе', RESOLVED: 'Решена', REJECTED: 'Отклонена',
};
const STATUSES: ModerationStatus[] = ['OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED'];
const HIDABLE: ModerationTargetKind[] = ['POST', 'COMMENT', 'GROUP'];
const DURATIONS = [
  { hours: 1, label: '1 час' }, { hours: 24, label: '1 сутки' }, { hours: 168, label: '7 суток' }, { hours: 720, label: '30 суток' },
];

/**
 * Рабочий экран модератора: очередь с фильтром, карточка жалобы с минимальным доказательством и журнал решений.
 * Кнопки показываются по роли и состоянию жалобы; сервер проверяет всё повторно и отвечает ошибкой, если нельзя.
 */
@Component({
  selector: 'app-moderation-page',
  imports: [AppButton, Skeleton, StatePanel],
  templateUrl: './moderation-page.html',
  styleUrl: './moderation-page.scss',
})
export class ModerationPage implements OnInit {
  private readonly api = inject(ModerationService);
  private readonly toasts = inject(ToastService);
  private readonly auth = inject(AuthService);
  private readonly user = inject(CurrentUser);

  protected readonly statuses = STATUSES;
  protected readonly durations = DURATIONS;
  protected readonly statusLabel = (s: ModerationStatus) => STATUS_LABELS[s];
  protected readonly kindLabel = (k: ModerationTargetKind) => KIND_LABELS[k];
  protected readonly reasonLabel = (r: string) => REASON_LABELS[r] ?? r;
  protected readonly actionLabel = (a: string) => ACTION_LABELS[a] ?? a;

  protected readonly isAdmin = computed(() => this.user.hasAnyRole(['ADMIN']));
  protected readonly tab = signal<'queue' | 'journal'>('queue');

  // очередь
  protected readonly status = signal<ModerationStatus>('OPEN');
  protected readonly items = signal<QueueItem[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly selected = signal<QueueItem | null>(null);
  protected readonly evidence = signal<Evidence | null>(null);
  protected readonly evidenceError = signal<string | null>(null);

  // действия по выбранной жалобе
  protected readonly reason = signal('');
  protected readonly durationHours = signal(24);
  protected readonly busy = signal(false);
  protected readonly actionError = signal<string | null>(null);

  // журнал
  protected readonly journal = signal<JournalEntry[]>([]);
  protected readonly journalNext = signal<string | null>(null);
  protected readonly journalHasMore = signal(false);
  protected readonly journalLoading = signal(true);

  /** Жалоба назначена мне и находится в работе — только тогда доступны решения. */
  protected readonly mine = computed(() => {
    const s = this.selected();
    return !!s && s.status === 'IN_REVIEW' && s.assigneeId === this.auth.user()?.id;
  });
  protected readonly authorId = computed(() => {
    const e = this.evidence();
    return e ? authorIdOf(e) : null;
  });

  private queueToken = 0;

  ngOnInit(): void {
    void this.loadQueue();
  }

  protected showTab(tab: 'queue' | 'journal'): void {
    this.tab.set(tab);
    if (tab === 'journal' && this.journal().length === 0) {
      void this.loadJournal(true);
    }
  }

  protected setStatus(status: ModerationStatus): void {
    if (status === this.status()) {
      return;
    }
    this.status.set(status);
    this.selected.set(null);
    this.evidence.set(null);
    void this.loadQueue();
  }

  protected async loadQueue(): Promise<void> {
    const token = ++this.queueToken;
    this.loading.set(true);
    this.error.set(null);
    try {
      const page = await this.api.queue(this.status(), null);
      if (token !== this.queueToken) {
        return;
      }
      this.items.set(page.items);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
    } catch (error) {
      if (token === this.queueToken) {
        this.error.set(toProblem(error).message);
      }
    } finally {
      if (token === this.queueToken) {
        this.loading.set(false);
      }
    }
  }

  protected async loadMore(): Promise<void> {
    const cursor = this.nextCursor();
    if (this.loadingMore() || !cursor) {
      return;
    }
    this.loadingMore.set(true);
    try {
      const page = await this.api.queue(this.status(), cursor);
      const seen = new Set(this.items().map((i) => i.id));
      this.items.set([...this.items(), ...page.items.filter((i) => !seen.has(i.id))]);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.loadingMore.set(false);
    }
  }

  protected async select(item: QueueItem): Promise<void> {
    this.selected.set(item);
    this.evidence.set(null);
    this.evidenceError.set(null);
    this.reason.set('');
    this.actionError.set(null);
    await this.loadEvidence(item.id);
  }

  private async loadEvidence(reportId: string): Promise<void> {
    try {
      this.evidence.set(await this.api.evidence(reportId));
    } catch (error) {
      this.evidenceError.set(toProblem(error).message);
    }
  }

  /** Тело снимка показываем только если оно есть: удалённый или скрытый объект снимка без текста не даёт. */
  protected evidenceText(e: Evidence): string | null {
    const body = e.snapshot?.['body'];
    return typeof body === 'string' ? body : null;
  }

  protected canHide(kind: ModerationTargetKind): boolean {
    return HIDABLE.includes(kind);
  }

  protected async take(): Promise<void> {
    await this.act(() => this.api.take(this.selected()!.id), 'Жалоба взята в работу.', false);
  }

  protected async hide(): Promise<void> {
    await this.act(() => this.api.resolve(this.selected()!.id, 'HIDE', this.reason().trim()), 'Материал скрыт, решение записано.');
  }

  protected async noAction(): Promise<void> {
    await this.act(() => this.api.resolve(this.selected()!.id, 'NO_ACTION', this.reason().trim()), 'Решение записано: нарушения нет.');
  }

  protected async reject(): Promise<void> {
    await this.act(() => this.api.reject(this.selected()!.id, this.reason().trim()), 'Жалоба отклонена.');
  }

  protected async restore(): Promise<void> {
    await this.act(() => this.api.restore(this.selected()!.id, this.reason().trim()), 'Материал восстановлен.');
  }

  protected async temporarySanction(): Promise<void> {
    const userId = this.authorId();
    if (!userId) {
      return;
    }
    await this.act(() => this.api.temporarySanction(userId, this.durationHours(), this.reason().trim()), 'Временная блокировка применена.');
  }

  protected async permanentSanction(): Promise<void> {
    const userId = this.authorId();
    if (!userId) {
      return;
    }
    await this.act(() => this.api.permanentSanction(userId, this.reason().trim()), 'Постоянная блокировка применена.');
  }

  /** Общий путь действия: причина обязательна (кроме взятия в работу), ошибка показывается на месте. */
  private async act(run: () => Promise<unknown>, success: string, needsReason = true): Promise<void> {
    if (needsReason && this.reason().trim().length === 0) {
      this.actionError.set('Укажите причину: без неё решение не сохранится.');
      return;
    }
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    try {
      await run();
      this.toasts.show(success, 'success');
      this.reason.set('');
      await this.refreshSelected();
    } catch (error) {
      this.actionError.set(toProblem(error).message);
    } finally {
      this.busy.set(false);
    }
  }

  /**
   * После действия жалоба могла перейти в другой статус (например, OPEN → IN_REVIEW). Фильтр переходит вместе
   * с ней, и жалоба остаётся выбранной: иначе её карточка и кнопки решения исчезли бы из экрана.
   */
  private async refreshSelected(): Promise<void> {
    const current = this.selected();
    if (!current) {
      return;
    }
    const evidence = await this.api.evidence(current.id);
    if (evidence.status !== this.status()) {
      this.status.set(evidence.status);
    }
    await this.loadQueue();
    const fresh = this.items().find((i) => i.id === current.id);
    if (fresh) {
      this.selected.set(fresh);
      await this.loadEvidence(fresh.id);
    } else {
      this.selected.set(null);
      this.evidence.set(null);
    }
  }

  protected async loadJournal(reset: boolean): Promise<void> {
    this.journalLoading.set(true);
    try {
      const page = await this.api.journal(reset ? null : this.journalNext());
      this.journal.set(reset ? page.items : [...this.journal(), ...page.items]);
      this.journalNext.set(page.nextCursor);
      this.journalHasMore.set(page.hasMore);
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.journalLoading.set(false);
    }
  }

  protected time(iso: string): string {
    return new Date(iso).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }
}
