import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PostService, PostView } from '../../../core/post.service';
import { CommentService, CommentView } from '../../../core/comment.service';
import { AuthService } from '../../../core/auth.service';
import { toProblem } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ReportButton } from '../../../shared/report/report-button';
import { ReportService } from '../../../core/report.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Avatar } from '../../../shared/ui/avatar/avatar';
import { Card } from '../../../shared/ui/card/card';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { MediaView } from '../../../shared/media/media-view/media-view';
import { ReactionBar } from '../../../shared/reaction-bar/reaction-bar';
import { CommentItem, CommentNode, buildCommentTree } from '../comment-item/comment-item';

/**
 * Карточка публикации с ветками комментариев (TASK-049) и реакциями (TASK-050). Комментарии
 * грузятся постранично по корневым записям; ответы второго/третьего уровня сервер отдаёт в той же
 * странице (TASK-048).
 */
@Component({
  selector: 'app-post-detail',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    RouterLink,
    AppButton,
    Avatar,
    Card,
    Skeleton,
    StatePanel,
    TextField,
    MediaView,
    ReactionBar,
    CommentItem,
    ReportButton,
  ],
  templateUrl: './post-detail.html',
  styleUrl: './post-detail.scss',
})
export class PostDetail implements OnInit {
  private readonly posts = inject(PostService);
  private readonly commentsApi = inject(CommentService);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);
  private readonly reports = inject(ReportService);
  /** Причина скрытия видна только автору скрытой публикации; для остальных остаётся null. */
  protected readonly hiddenReason = signal<string | null>(null);

  readonly id = input.required<string>();

  protected readonly post = signal<PostView | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly isOwn = computed(() => this.auth.user()?.id === this.post()?.authorId);
  protected readonly edited = computed(() => {
    const p = this.post();
    if (!p || !p.publishedAt) {
      return false;
    }
    return new Date(p.updatedAt).getTime() > new Date(p.publishedAt).getTime();
  });

  protected readonly commentNodes = signal<CommentNode[]>([]);
  protected readonly commentsLoading = signal(true);
  protected readonly commentsError = signal<string | null>(null);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMoreComments = signal(false);
  protected readonly loadingMoreComments = signal(false);

  protected readonly newCommentControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(2000)],
  });
  protected readonly postingComment = signal(false);
  protected readonly postCommentError = signal<string | null>(null);

  private allFlatComments: CommentView[] = [];

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    try {
      const loaded = await this.posts.get(this.id());
      this.post.set(loaded);
      if (loaded.status === 'HIDDEN' && this.auth.user()?.id === loaded.authorId) {
        this.hiddenReason.set((await this.reports.hiddenPostReason(loaded.id)).reason);
      }
      await this.loadComments();
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

  private async loadComments(): Promise<void> {
    this.commentsLoading.set(true);
    this.commentsError.set(null);
    try {
      const page = await this.commentsApi.list(this.id(), null);
      this.allFlatComments = page.items;
      this.commentNodes.set(buildCommentTree(page.items));
      this.nextCursor.set(page.nextCursor);
      this.hasMoreComments.set(page.hasMore);
    } catch (error) {
      this.commentsError.set(toProblem(error).message);
    } finally {
      this.commentsLoading.set(false);
    }
  }

  protected async loadMoreComments(): Promise<void> {
    if (this.loadingMoreComments() || !this.hasMoreComments()) {
      return;
    }
    this.loadingMoreComments.set(true);
    try {
      const page = await this.commentsApi.list(this.id(), this.nextCursor());
      const seen = new Set(this.allFlatComments.map((c) => c.id));
      this.allFlatComments = [...this.allFlatComments, ...page.items.filter((c) => !seen.has(c.id))];
      this.commentNodes.set(buildCommentTree(this.allFlatComments));
      this.nextCursor.set(page.nextCursor);
      this.hasMoreComments.set(page.hasMore);
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.loadingMoreComments.set(false);
    }
  }

  /** Структуру (глубина, адресат переполнения) знает только сервер — после изменения список перечитывается. */
  protected onCommentsChanged(): void {
    void this.loadComments();
  }

  protected async submitComment(): Promise<void> {
    if (this.newCommentControl.invalid || this.postingComment()) {
      this.newCommentControl.markAsTouched();
      return;
    }
    this.postingComment.set(true);
    this.postCommentError.set(null);
    try {
      await this.commentsApi.create(this.id(), this.newCommentControl.value.trim(), null);
      this.newCommentControl.setValue('');
      await this.loadComments();
    } catch (error) {
      this.postCommentError.set(toProblem(error).message);
    } finally {
      this.postingComment.set(false);
    }
  }
}
