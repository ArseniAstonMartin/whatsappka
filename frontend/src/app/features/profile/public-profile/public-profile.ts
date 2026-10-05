import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { ProfileService, PublicProfile, Relations, CursorPage } from '../../../core/profile.service';
import { ChatService } from '../../../core/chat.service';
import { PostService, PostSummaryPublic } from '../../../core/post.service';
import { toProblem } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { ProfileHeader } from '../profile-header/profile-header';
import { PostList } from '../../../shared/post-list/post-list';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { Router, RouterLink } from '@angular/router';

/**
 * Чужой профиль по имени пользователя. Действие подписки меняет состояние только после ответа сервера;
 * блокировка появится отдельно, поэтому здесь её нет.
 */
@Component({
  selector: 'app-public-profile',
  imports: [AppButton, Skeleton, StatePanel, ProfileHeader, PostList, RouterLink],
  templateUrl: './public-profile.html',
  styleUrl: './public-profile.scss',
})
export class PublicProfilePage implements OnInit {
  private readonly profiles = inject(ProfileService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly chats = inject(ChatService);
  private readonly posts = inject(PostService);
  private readonly router = inject(Router);

  /** Ссылка стабильна между рендерами; актуального автора читает в момент вызова. */
  protected readonly loadProfilePosts = (cursor: string | null): Promise<CursorPage<PostSummaryPublic>> => {
    const profile = this.profile();
    return profile ? this.posts.profilePosts(profile.id, cursor) : Promise.resolve({ items: [], nextCursor: null, hasMore: false });
  };

  readonly username = input.required<string>();

  protected readonly profile = signal<PublicProfile | null>(null);
  protected readonly relations = signal<Relations | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly followBusy = signal(false);
  protected readonly writeBusy = signal(false);

  protected readonly followLabel = computed(() => {
    const r = this.relations();
    if (!r) return '';
    return r.followedByViewer ? 'Отписаться' : 'Подписаться';
  });

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    try {
      const profile = await this.profiles.publicByUsername(this.username());
      this.profile.set(profile);
      this.relations.set(await this.profiles.relations(profile.id));
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

  protected async toggleFollow(): Promise<void> {
    const profile = this.profile();
    const current = this.relations();
    if (!profile || !current || this.followBusy()) {
      return;
    }
    this.followBusy.set(true);
    try {
      const next = !current.followedByViewer;
      await this.profiles.setFollowing(profile.id, next);
      this.relations.set(await this.profiles.relations(profile.id));
      this.toasts.show(next ? 'Вы подписались' : 'Подписка отменена', 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.followBusy.set(false);
    }
  }

  /** Открывает личный диалог; если его ещё нет, сервер создаёт его. Блокировка сервером отвечает 404. */
  protected async write(): Promise<void> {
    const profile = this.profile();
    if (!profile || this.writeBusy()) {
      return;
    }
    this.writeBusy.set(true);
    try {
      const { id } = await this.chats.open(profile.id);
      await this.router.navigate(['/chats', id]);
    } catch (error) {
      const problem = toProblem(error);
      this.toasts.show(
        problem.status === 404 ? 'Написать этому пользователю нельзя: возможно, есть блокировка.' : problem.message,
        'error',
      );
    } finally {
      this.writeBusy.set(false);
    }
  }

  /**
   * Блокировка после подтверждения. Ответ сервера до подтверждения не меняет экран; при успехе профиль скрывается,
   * поэтому разблокировка выполняется в настройках.
   */
  protected async block(): Promise<void> {
    const profile = this.profile();
    if (!profile || this.followBusy()) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: `Заблокировать ${profile.displayName}?`,
      message:
        'Личная переписка сохранится, но новые личные сообщения, приглашения и подписки станут недоступны. ' +
        'Ваши подписки друг на друга будут удалены. Сообщения в общих групповых чатах остаются видны всем участникам. ' +
        'Разблокировать можно в настройках.',
      confirmLabel: 'Заблокировать',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.followBusy.set(true);
    try {
      await this.profiles.block(profile.id);
      this.toasts.show('Пользователь заблокирован. Управлять блокировками можно в настройках.', 'success');
      await this.load();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.followBusy.set(false);
    }
  }
}
