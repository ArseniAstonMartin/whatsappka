import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CommentService, CommentView } from '../../../core/comment.service';
import { AuthService } from '../../../core/auth.service';
import { toProblem } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Avatar } from '../../../shared/ui/avatar/avatar';
import { MediaView } from '../../../shared/media/media-view/media-view';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { ReactionBar } from '../../../shared/reaction-bar/reaction-bar';
import { ReportButton } from '../../../shared/report/report-button';

export interface CommentNode {
  comment: CommentView;
  replyToDisplayName: string | null;
  children: CommentNode[];
}

/** Строит дерево из плоского списка (TASK-048 отдаёт глубину и parentId, а не вложенность сама). */
export function buildCommentTree(flat: CommentView[]): CommentNode[] {
  const nameByAuthorId = new Map<string, string>();
  for (const c of flat) {
    nameByAuthorId.set(c.authorId, c.authorDisplayName);
  }
  const byId = new Map<string, CommentNode>();
  for (const c of flat) {
    byId.set(c.id, {
      comment: c,
      replyToDisplayName: c.replyToUserId ? (nameByAuthorId.get(c.replyToUserId) ?? null) : null,
      children: [],
    });
  }
  const roots: CommentNode[] = [];
  for (const c of flat) {
    const node = byId.get(c.id)!;
    if (c.parentId && byId.has(c.parentId)) {
      byId.get(c.parentId)!.children.push(node);
    } else if (!c.parentId) {
      roots.push(node);
    }
  }
  return roots;
}

/**
 * Один комментарий ветки с ответом/редактированием/удалением (TASK-049). Глубина не обрабатывается
 * здесь отдельно — сервер (TASK-048) уже не даёт дереву стать глубже трёх уровней.
 */
@Component({
  selector: 'app-comment-item',
  imports: [FormsModule, ReactiveFormsModule, DatePipe, RouterLink, AppButton, Avatar, MediaView, TextField, ReactionBar, CommentItem, ReportButton],
  templateUrl: './comment-item.html',
  styleUrl: './comment-item.scss',
})
export class CommentItem {
  private readonly auth = inject(AuthService);
  private readonly comments = inject(CommentService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  readonly node = input.required<CommentNode>();
  readonly postId = input.required<string>();
  /** Структура доступна только серверу — после любого изменения родитель перечитывает список. */
  readonly changed = output<void>();

  protected readonly isOwn = computed(() => this.auth.user()?.id === this.node().comment.authorId);

  protected readonly replying = signal(false);
  protected readonly replyControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(2000)],
  });
  protected readonly replyBusy = signal(false);
  protected readonly replyError = signal<string | null>(null);

  protected readonly editing = signal(false);
  protected readonly editControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(2000)],
  });
  protected readonly editBusy = signal(false);
  protected readonly editError = signal<string | null>(null);

  protected readonly removing = signal(false);

  protected toggleReply(): void {
    this.replying.update((v) => !v);
    if (this.replying()) {
      this.replyControl.setValue('');
      this.replyError.set(null);
    }
  }

  protected async submitReply(): Promise<void> {
    if (this.replyControl.invalid || this.replyBusy()) {
      this.replyControl.markAsTouched();
      return;
    }
    this.replyBusy.set(true);
    this.replyError.set(null);
    try {
      await this.comments.create(this.postId(), this.replyControl.value.trim(), this.node().comment.id);
      this.replying.set(false);
      this.changed.emit();
    } catch (error) {
      this.replyError.set(toProblem(error).message);
    } finally {
      this.replyBusy.set(false);
    }
  }

  protected toggleEdit(): void {
    this.editing.update((v) => !v);
    if (this.editing()) {
      this.editControl.setValue(this.node().comment.body ?? '');
      this.editError.set(null);
    }
  }

  protected async submitEdit(): Promise<void> {
    if (this.editControl.invalid || this.editBusy()) {
      this.editControl.markAsTouched();
      return;
    }
    this.editBusy.set(true);
    this.editError.set(null);
    try {
      await this.comments.update(this.node().comment.id, this.editControl.value.trim(), this.node().comment.version);
      this.editing.set(false);
      this.changed.emit();
    } catch (error) {
      this.editError.set(toProblem(error).message);
    } finally {
      this.editBusy.set(false);
    }
  }

  protected async remove(): Promise<void> {
    if (this.removing()) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: 'Удалить комментарий?',
      message: 'Ответы на него останутся видны. Действие нельзя отменить из интерфейса.',
      confirmLabel: 'Удалить',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.removing.set(true);
    try {
      await this.comments.remove(this.node().comment.id);
      this.toasts.show('Комментарий удалён', 'success');
      this.changed.emit();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.removing.set(false);
    }
  }
}
