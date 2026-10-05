import { Component, inject, signal } from '@angular/core';
import { ProfileService, OwnProfile, CursorPage } from '../../../core/profile.service';
import { PostService, PostSummaryPublic } from '../../../core/post.service';
import { toProblem } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Card } from '../../../shared/ui/card/card';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { MediaUpload, UploadedMedia } from '../../../shared/media/media-upload/media-upload';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { ProfileHeader } from '../profile-header/profile-header';
import { ProfileEdit } from '../profile-edit/profile-edit';
import { PostList } from '../../../shared/post-list/post-list';

/** Собственный профиль: просмотр, редактирование и замена аватара и обложки через привязку медиа. */
@Component({
  selector: 'app-own-profile',
  imports: [AppButton, Card, Skeleton, MediaUpload, StatePanel, ProfileHeader, ProfileEdit, PostList],
  templateUrl: './own-profile.html',
  styleUrl: './own-profile.scss',
})
export class OwnProfilePage {
  private readonly profiles = inject(ProfileService);
  private readonly posts = inject(PostService);
  private readonly toasts = inject(ToastService);

  protected readonly loadOwnPosts = (cursor: string | null): Promise<CursorPage<PostSummaryPublic>> => {
    const profile = this.profile();
    return profile ? this.posts.profilePosts(profile.id, cursor) : Promise.resolve({ items: [], nextCursor: null, hasMore: false });
  };

  protected readonly profile = signal<OwnProfile | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly editing = signal(false);

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.profile.set(await this.profiles.own());
    } catch (error) {
      this.error.set(toProblem(error).message);
    } finally {
      this.loading.set(false);
    }
  }

  protected toggleEdit(): void {
    this.editing.update((value) => !value);
  }

  /** Сохранение текстовых полей не закрывает редактор: в нём же загрузка фото. */
  protected onSaved(saved: OwnProfile): void {
    this.profile.set(saved);
  }

  /** Аватар и обложка: файл готов — привязываем, затем перечитываем профиль. */
  protected async attach(kind: 'PROFILE_AVATAR' | 'PROFILE_COVER', media: UploadedMedia): Promise<void> {
    const profile = this.profile();
    if (!profile) {
      return;
    }
    try {
      await this.profiles.attach(media.id, kind, profile.id);
      this.toasts.show(kind === 'PROFILE_AVATAR' ? 'Аватар обновлён' : 'Обложка обновлена', 'success');
      await this.load();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }

  protected async detach(kind: 'PROFILE_AVATAR' | 'PROFILE_COVER', mediaId: string | null): Promise<void> {
    const profile = this.profile();
    if (!profile || !mediaId) {
      return;
    }
    try {
      await this.profiles.detach(mediaId, kind, profile.id);
      await this.load();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }
}
