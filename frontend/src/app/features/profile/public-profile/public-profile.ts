import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { ProfileService, PublicProfile, Relations } from '../../../core/profile.service';
import { toProblem } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { ProfileHeader } from '../profile-header/profile-header';

/**
 * Чужой профиль по имени пользователя. Действие подписки меняет состояние только после ответа сервера;
 * блокировка появится отдельно, поэтому здесь её нет.
 */
@Component({
  selector: 'app-public-profile',
  imports: [AppButton, Skeleton, StatePanel, ProfileHeader],
  templateUrl: './public-profile.html',
  styleUrl: './public-profile.scss',
})
export class PublicProfilePage implements OnInit {
  private readonly profiles = inject(ProfileService);
  private readonly toasts = inject(ToastService);

  readonly username = input.required<string>();

  protected readonly profile = signal<PublicProfile | null>(null);
  protected readonly relations = signal<Relations | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly followBusy = signal(false);

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
}
