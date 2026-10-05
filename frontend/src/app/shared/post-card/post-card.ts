import { Component, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PostService, PostSummaryPublic } from '../../core/post.service';
import { AuthService } from '../../core/auth.service';
import { toProblem } from '../../core/api-error';
import { ToastService } from '../ui/toast/toast.service';
import { ConfirmService } from '../ui/confirm-dialog/confirm.service';
import { AppButton } from '../ui/button/app-button';
import { Avatar } from '../ui/avatar/avatar';
import { Card } from '../ui/card/card';
import { MediaView } from '../media/media-view/media-view';
import { ReactionBar } from '../reaction-bar/reaction-bar';

/**
 * Одна карточка опубликованного поста: автор, текст, изображения, хештеги, отметка правки, реакции
 * (TASK-051). Действия (редактировать/удалить) видны только автору — то же решает и сервер (TASK-047).
 */
@Component({
  selector: 'app-post-card',
  imports: [DatePipe, RouterLink, AppButton, Avatar, Card, MediaView, ReactionBar],
  templateUrl: './post-card.html',
  styleUrl: './post-card.scss',
})
export class PostCard {
  private readonly auth = inject(AuthService);
  private readonly posts = inject(PostService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  readonly post = input.required<PostSummaryPublic>();
  /** Сообщает родительскому списку, что карточку нужно убрать после удаления. */
  readonly removed = output<string>();

  protected readonly removing = signal(false);

  protected readonly isOwn = computed(() => this.auth.user()?.id === this.post().authorId);
  protected readonly edited = computed(() => {
    const p = this.post();
    return new Date(p.updatedAt).getTime() > new Date(p.publishedAt).getTime();
  });

  protected async remove(): Promise<void> {
    if (this.removing()) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: 'Удалить запись?',
      message: 'Запись станет недоступна по обычным ссылкам. Действие нельзя отменить из интерфейса.',
      confirmLabel: 'Удалить',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.removing.set(true);
    try {
      await this.posts.remove(this.post().id);
      this.toasts.show('Запись удалена', 'success');
      this.removed.emit(this.post().id);
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.removing.set(false);
    }
  }
}
