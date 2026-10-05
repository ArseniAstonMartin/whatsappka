import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { ReactionService, ReactionType } from '../../core/reaction.service';
import { toProblem } from '../../core/api-error';
import { ToastService } from '../ui/toast/toast.service';

export type ReactionTarget = 'post' | 'comment';

interface ReactionState {
  counts: Partial<Record<ReactionType, number>>;
  viewerReaction: ReactionType | null;
}

const OPTIONS: { type: ReactionType; emoji: string; label: string }[] = [
  { type: 'LIKE', emoji: '👍', label: 'Нравится' },
  { type: 'HEART', emoji: '❤️', label: 'Любовь' },
  { type: 'LAUGH', emoji: '😆', label: 'Смешно' },
  { type: 'WOW', emoji: '😮', label: 'Вау' },
  { type: 'SAD', emoji: '😢', label: 'Грусть' },
  { type: 'SUPPORT', emoji: '🤝', label: 'Поддержка' },
];

function applyChange(state: ReactionState, next: ReactionType | null): ReactionState {
  const counts = { ...state.counts };
  if (state.viewerReaction) {
    counts[state.viewerReaction] = Math.max(0, (counts[state.viewerReaction] ?? 1) - 1);
  }
  if (next) {
    counts[next] = (counts[next] ?? 0) + 1;
  }
  return { counts, viewerReaction: next };
}

/**
 * Панель из шести реакций (TASK-050, FR-05). Повтор выбранной реакции снимает её, другой тип
 * заменяет предыдущую. Отображение обновляется оптимистично и откатывается при ошибке запроса —
 * сервер остаётся источником истины при следующей загрузке данных родителем.
 */
@Component({
  selector: 'app-reaction-bar',
  templateUrl: './reaction-bar.html',
  styleUrl: './reaction-bar.scss',
})
export class ReactionBar {
  private readonly reactions = inject(ReactionService);
  private readonly toasts = inject(ToastService);

  readonly target = input.required<ReactionTarget>();
  readonly targetId = input.required<string>();
  readonly counts = input.required<Partial<Record<ReactionType, number>>>();
  readonly viewerReaction = input.required<ReactionType | null>();

  protected readonly options = OPTIONS;
  protected readonly pickerOpen = signal(false);
  protected readonly busy = signal(false);
  private readonly override = signal<ReactionState | null>(null);

  protected readonly state = computed<ReactionState>(
    () => this.override() ?? { counts: this.counts(), viewerReaction: this.viewerReaction() },
  );
  protected readonly total = computed(() =>
    Object.values(this.state().counts).reduce((sum: number, n) => sum + (n ?? 0), 0),
  );
  protected readonly mine = computed(() => OPTIONS.find((o) => o.type === this.state().viewerReaction) ?? null);

  constructor() {
    effect(() => {
      this.counts();
      this.viewerReaction();
      this.override.set(null);
    });
  }

  protected countFor(type: ReactionType): number {
    return this.state().counts[type] ?? 0;
  }

  protected togglePicker(): void {
    this.pickerOpen.update((v) => !v);
  }

  protected closePicker(): void {
    this.pickerOpen.set(false);
  }

  protected async pick(type: ReactionType): Promise<void> {
    this.pickerOpen.set(false);
    if (this.busy()) {
      return;
    }
    const current = this.state();
    const next: ReactionType | null = current.viewerReaction === type ? null : type;
    this.override.set(applyChange(current, next));
    this.busy.set(true);
    try {
      if (this.target() === 'post') {
        if (next) {
          await this.reactions.setPostReaction(this.targetId(), next);
        } else {
          await this.reactions.removePostReaction(this.targetId());
        }
      } else {
        if (next) {
          await this.reactions.setCommentReaction(this.targetId(), next);
        } else {
          await this.reactions.removeCommentReaction(this.targetId());
        }
      }
    } catch (error) {
      this.override.set(null);
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.busy.set(false);
    }
  }
}
