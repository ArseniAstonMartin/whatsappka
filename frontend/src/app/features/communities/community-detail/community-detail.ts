import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CommunityService, CommunityView, CommunityVisibility } from '../../../core/community.service';
import { CursorPage } from '../../../core/profile.service';
import { PostService, PostSummaryPublic } from '../../../core/post.service';
import { toProblem, ApiProblem } from '../../../core/api-error';
import { applyServerErrors } from '../../auth/auth-form';
import { controlErrorText } from '../../../shared/ui/messages';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Card } from '../../../shared/ui/card/card';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { MediaUpload, UploadedMedia } from '../../../shared/media/media-upload/media-upload';
import { MediaView } from '../../../shared/media/media-view/media-view';
import { Avatar } from '../../../shared/ui/avatar/avatar';
import { CommunityMembers } from '../community-members/community-members';
import { PostList } from '../../../shared/post-list/post-list';
import { ReportButton } from '../../../shared/report/report-button';

const ROLE_LABEL: Record<string, string> = { OWNER: 'Владелец', ADMIN: 'Администратор', MEMBER: 'Участник' };

/**
 * Карточка сообщества: просмотр, вступление/заявка для постороннего, настройки для владельца,
 * выход для участника и управление составом/заявками/приглашениями для OWNER/ADMIN (TASK-040).
 */
@Component({
  selector: 'app-community-detail',
  imports: [
    ReportButton,
    ReactiveFormsModule,
    AppButton,
    Card,
    Skeleton,
    StatePanel,
    TextField,
    MediaUpload,
    MediaView,
    Avatar,
    CommunityMembers,
    PostList,
  ],
  templateUrl: './community-detail.html',
  styleUrl: './community-detail.scss',
})
export class CommunityDetail implements OnInit {
  private readonly communities = inject(CommunityService);
  private readonly posts = inject(PostService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly router = inject(Router);

  readonly slug = input.required<string>();

  protected readonly loadGroupPosts = (cursor: string | null): Promise<CursorPage<PostSummaryPublic>> => {
    const view = this.view();
    return view ? this.posts.groupPosts(view.id, cursor) : Promise.resolve({ items: [], nextCursor: null, hasMore: false });
  };

  protected readonly view = signal<CommunityView | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly joinBusy = signal(false);
  protected readonly myRequestId = signal<string | null>(null);

  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly removing = signal(false);
  protected readonly leaving = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly formMessage = computed(() => this.problem()?.message ?? null);

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(2000)] }),
    visibility: new FormControl<CommunityVisibility>('PUBLIC', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    for (const control of Object.values(this.form.controls) as AbstractControl[]) {
      control.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        if (control.hasError('server')) {
          const { server: _ignored, ...rest } = control.errors ?? {};
          control.setErrors(Object.keys(rest).length ? rest : null);
        }
      });
    }
  }

  ngOnInit(): void {
    void this.load();
  }

  protected roleLabel(): string | null {
    const role = this.view()?.viewerRole;
    return role ? (ROLE_LABEL[role] ?? role) : null;
  }

  protected errorFor(name: 'name' | 'description'): string | null {
    return controlErrorText(this.form.controls[name]);
  }

  protected descriptionLength(): number {
    return this.form.controls.description.value.length;
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    try {
      const view = await this.communities.bySlug(this.slug());
      this.view.set(view);
      this.resetForm(view);
      if (view.visibility === 'PRIVATE' && view.viewerRole === null) {
        await this.loadMyRequest(view.id);
      } else {
        this.myRequestId.set(null);
      }
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

  private async loadMyRequest(groupId: string): Promise<void> {
    try {
      const pending = await this.communities.myJoinRequest(groupId);
      this.myRequestId.set(pending?.id ?? null);
    } catch {
      this.myRequestId.set(null);
    }
  }

  protected async join(): Promise<void> {
    const view = this.view();
    if (!view || this.joinBusy()) {
      return;
    }
    this.joinBusy.set(true);
    try {
      await this.communities.join(view.id);
      this.toasts.show('Вы вступили в сообщество', 'success');
      await this.load();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.joinBusy.set(false);
    }
  }

  protected async requestJoin(): Promise<void> {
    const view = this.view();
    if (!view || this.joinBusy()) {
      return;
    }
    this.joinBusy.set(true);
    try {
      const request = await this.communities.requestJoin(view.id);
      this.myRequestId.set(request.id);
      this.toasts.show('Заявка отправлена', 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.joinBusy.set(false);
    }
  }

  protected async cancelRequest(): Promise<void> {
    const view = this.view();
    const requestId = this.myRequestId();
    if (!view || !requestId || this.joinBusy()) {
      return;
    }
    this.joinBusy.set(true);
    try {
      await this.communities.cancelJoinRequest(view.id, requestId);
      this.myRequestId.set(null);
      this.toasts.show('Заявка отменена');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.joinBusy.set(false);
    }
  }

  /** Владелец не выходит без передачи владения или удаления — сервер отклонит, форма это не дублирует. */
  protected async leave(): Promise<void> {
    const view = this.view();
    if (!view || this.leaving()) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: `Покинуть «${view.name}»?`,
      message: 'Доступ к закрытому составу и публикациям сообщества прекратится. Вернуться можно будет по новой заявке или приглашению.',
      confirmLabel: 'Покинуть',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.leaving.set(true);
    try {
      await this.communities.leave(view.id);
      this.toasts.show('Вы покинули сообщество');
      await this.load();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.leaving.set(false);
    }
  }

  protected async onMembersChanged(): Promise<void> {
    await this.load();
  }

  protected toggleEdit(): void {
    this.editing.update((value) => !value);
    const view = this.view();
    if (view) {
      this.resetForm(view);
    }
  }

  /** Смена PRIVATE→PUBLIC требует подтверждения: каталог, состав и публикации станут видны всем. */
  protected async save(): Promise<void> {
    const view = this.view();
    if (!view || this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (view.visibility === 'PRIVATE' && value.visibility === 'PUBLIC') {
      const confirmed = await this.confirm.confirm({
        title: 'Сделать сообщество открытым?',
        message: 'Каталог, состав участников и публикации станут видны всем. Вернуть приватность можно будет позже тем же образом.',
        confirmLabel: 'Сделать открытым',
        danger: true,
      });
      if (!confirmed) {
        return;
      }
    }
    this.saving.set(true);
    this.problem.set(null);
    try {
      await this.communities.update(view.id, {
        name: value.name.trim(),
        description: value.description,
        visibility: value.visibility,
      });
      this.toasts.show('Сообщество сохранено', 'success');
      await this.load();
    } catch (error) {
      const problem = toProblem(error);
      this.problem.set(problem);
      applyServerErrors(this.form, problem);
    } finally {
      this.saving.set(false);
    }
  }

  protected async attach(kind: 'avatar' | 'cover', media: UploadedMedia): Promise<void> {
    const view = this.view();
    if (!view) {
      return;
    }
    try {
      if (kind === 'avatar') {
        await this.communities.attachAvatar(view.id, media.id);
      } else {
        await this.communities.attachCover(view.id, media.id);
      }
      this.toasts.show(kind === 'avatar' ? 'Аватар обновлён' : 'Обложка обновлена', 'success');
      await this.load();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }

  /** Удаление — мягкое на сервере, но для участников и каталога сообщество сразу недоступно. */
  protected async remove(): Promise<void> {
    const view = this.view();
    if (!view || this.removing()) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: `Удалить сообщество «${view.name}»?`,
      message: 'Сообщество станет недоступно участникам, каталог и состав исчезнут. Действие нельзя отменить из интерфейса.',
      confirmLabel: 'Удалить',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.removing.set(true);
    try {
      await this.communities.remove(view.id);
      this.toasts.show('Сообщество удалено', 'success');
      await this.router.navigate(['/groups']);
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.removing.set(false);
    }
  }

  private resetForm(view: CommunityView): void {
    this.form.reset({
      name: view.name,
      description: view.description ?? '',
      visibility: view.visibility,
    });
  }
}
