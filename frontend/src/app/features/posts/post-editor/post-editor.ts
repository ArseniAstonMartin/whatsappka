import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { PostService, PostStatus } from '../../../core/post.service';
import { toProblem } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Card } from '../../../shared/ui/card/card';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { MediaUpload, UploadedMedia } from '../../../shared/media/media-upload/media-upload';
import { MediaView } from '../../../shared/media/media-view/media-view';

type SaveState = 'idle' | 'saving' | 'saved' | 'unsaved' | 'error';

const MEDIA_MAX = 10;
const AUTOSAVE_DELAY_MS = 2000;

const SCHEDULE_FAILURE_LABEL: Record<string, string> = {
  empty_post: 'Нужен текст или хотя бы одно готовое изображение',
  not_group_member: 'Вы больше не состоите в группе этой записи',
  author_inactive: 'Аккаунт был неактивен в момент публикации',
};

/**
 * Редактор поста (TASK-046): текст, готовые изображения, хештеги; explicit save и autosave через
 * 2 секунды. Публикация сейчас и расписание отправляют текущий текст перед действием, чтобы не
 * опубликовать устаревшее содержимое. Конфликт версии не перезаписывает ввод — только явное
 * «Обновить» подтягивает серверное состояние.
 */
@Component({
  selector: 'app-post-editor',
  imports: [ReactiveFormsModule, AppButton, Card, Skeleton, StatePanel, TextField, MediaUpload, MediaView],
  templateUrl: './post-editor.html',
  styleUrl: './post-editor.scss',
})
export class PostEditor implements OnInit, OnDestroy {
  private readonly posts = inject(PostService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly router = inject(Router);

  readonly id = input<string>();
  readonly groupId = input<string>();

  protected readonly loading = signal(false);
  protected readonly notFound = signal(false);
  protected readonly loadError = signal<string | null>(null);

  protected readonly postId = signal<string | null>(null);
  protected readonly status = signal<PostStatus>('DRAFT');
  protected readonly version = signal(0);
  protected readonly scheduleFailureReason = signal<string | null>(null);
  protected readonly postGroupId = signal<string | null>(null);

  protected readonly saveState = signal<SaveState>('idle');
  protected readonly saveError = signal<string | null>(null);
  protected readonly conflicted = signal(false);

  protected readonly existingMedia = signal<string[]>([]);
  protected readonly newSlotIds = signal<string[]>([]);
  protected readonly newSlotMedia = signal<Record<string, string>>({});
  protected readonly mediaIds = computed(() => {
    const map = this.newSlotMedia();
    return [...this.existingMedia(), ...this.newSlotIds().map((slotId) => map[slotId]).filter((v): v is string => !!v)];
  });
  protected readonly mediaMax = MEDIA_MAX;

  protected readonly hashtags = signal<string[]>([]);
  protected readonly hashtagInput = new FormControl('', { nonNullable: true });

  protected readonly scheduleAt = signal('');
  protected readonly scheduleMin = minScheduleLocal();
  protected readonly timeZoneName = Intl.DateTimeFormat().resolvedOptions().timeZone;
  protected readonly scheduleBusy = signal(false);
  protected readonly publishBusy = signal(false);
  protected readonly removing = signal(false);

  protected readonly form = new FormGroup({
    body: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(10000)] }),
  });

  private autosaveTimer: ReturnType<typeof setTimeout> | null = null;
  private suppressChangeTracking = false;

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (!this.suppressChangeTracking) {
        this.scheduleAutosave();
      }
    });
  }

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.postId.set(id);
      void this.loadExisting(id);
    } else {
      this.postGroupId.set(this.groupId() ?? null);
      this.newSlotIds.set(['slot-0']);
    }
  }

  ngOnDestroy(): void {
    if (this.autosaveTimer) {
      clearTimeout(this.autosaveTimer);
    }
  }

  protected bodyLength(): number {
    return this.form.controls.body.value.length;
  }

  protected canAddMedia(): boolean {
    return this.mediaIds().length < MEDIA_MAX;
  }

  protected async loadExisting(id: string): Promise<void> {
    this.loading.set(true);
    this.notFound.set(false);
    this.loadError.set(null);
    this.conflicted.set(false);
    try {
      const post = await this.posts.get(id);
      this.suppressChangeTracking = true;
      this.form.reset({ body: post.body ?? '' });
      this.suppressChangeTracking = false;
      this.status.set(post.status);
      this.version.set(post.version);
      this.scheduleFailureReason.set(post.scheduleFailureReason);
      this.postGroupId.set(post.groupId);
      this.existingMedia.set(post.mediaIds);
      this.newSlotMedia.set({});
      this.newSlotIds.set(post.mediaIds.length < MEDIA_MAX ? ['slot-0'] : []);
      this.hashtags.set(post.hashtags);
      this.saveState.set('idle');
      this.saveError.set(null);
    } catch (error) {
      const problem = toProblem(error);
      if (problem.status === 404) {
        this.notFound.set(true);
      } else {
        this.loadError.set(problem.message);
      }
    } finally {
      this.loading.set(false);
    }
  }

  protected scheduleAutosave(): void {
    if (this.saveState() !== 'saving') {
      this.saveState.set('unsaved');
    }
    if (this.autosaveTimer) {
      clearTimeout(this.autosaveTimer);
    }
    this.autosaveTimer = setTimeout(() => void this.save(), AUTOSAVE_DELAY_MS);
  }

  /** Явное и автосохранение используют один путь: ложного «сохранено» без ответа сервера не бывает. */
  protected async save(): Promise<boolean> {
    if (this.saveState() === 'saving') {
      return false;
    }
    if (this.autosaveTimer) {
      clearTimeout(this.autosaveTimer);
      this.autosaveTimer = null;
    }
    const body = this.form.controls.body.value.trim() || null;
    const media = this.mediaIds();
    const tags = this.hashtags();
    this.saveState.set('saving');
    try {
      if (!this.postId()) {
        const ref = await this.posts.create(this.postGroupId(), body, media, tags);
        this.postId.set(ref.id);
        this.version.set(0);
        this.saveState.set('saved');
        this.saveError.set(null);
        await this.router.navigate(['/posts', ref.id, 'edit'], { replaceUrl: true });
        return true;
      }
      await this.posts.update(this.postId()!, this.version(), body, media, tags);
      this.version.update((v) => v + 1);
      this.saveState.set('saved');
      this.saveError.set(null);
      this.conflicted.set(false);
      this.scheduleFailureReason.set(null);
      return true;
    } catch (error) {
      const problem = toProblem(error);
      this.saveState.set('error');
      this.saveError.set(problem.message);
      this.conflicted.set(problem.code === 'version_conflict');
      return false;
    }
  }

  protected async reloadFromServer(): Promise<void> {
    const id = this.postId();
    if (id) {
      await this.loadExisting(id);
    }
  }

  protected onSlotReady(slotId: string, media: UploadedMedia): void {
    this.newSlotMedia.update((map) => ({ ...map, [slotId]: media.id }));
    if (this.mediaIds().length < MEDIA_MAX) {
      this.newSlotIds.update((ids) => [...ids, `slot-${Date.now()}`]);
    }
    this.scheduleAutosave();
  }

  protected onSlotRemoved(slotId: string): void {
    this.newSlotMedia.update((map) => {
      const next = { ...map };
      delete next[slotId];
      return next;
    });
    this.newSlotIds.update((ids) => (ids.length > 1 ? ids.filter((s) => s !== slotId) : ids));
    this.scheduleAutosave();
  }

  protected removeExistingMedia(mediaId: string): void {
    this.existingMedia.update((ids) => ids.filter((m) => m !== mediaId));
    if (this.newSlotIds().length === 0) {
      this.newSlotIds.set([`slot-${Date.now()}`]);
    }
    this.scheduleAutosave();
  }

  protected addHashtag(): void {
    const raw = this.hashtagInput.value.trim().replace(/^#/, '');
    this.hashtagInput.setValue('');
    if (!raw || this.hashtags().length >= 10 || this.hashtags().includes(raw.toLowerCase())) {
      return;
    }
    this.hashtags.update((tags) => [...tags, raw.toLowerCase()]);
    this.scheduleAutosave();
  }

  protected removeHashtag(tag: string): void {
    this.hashtags.update((tags) => tags.filter((t) => t !== tag));
    this.scheduleAutosave();
  }

  protected scheduleFailureLabel(): string | null {
    const reason = this.scheduleFailureReason();
    return reason ? (SCHEDULE_FAILURE_LABEL[reason] ?? reason) : null;
  }

  /** Публикует текущий ввод: сперва сохраняет его, иначе ушла бы прежняя сохранённая версия. */
  protected async publishNow(): Promise<void> {
    if (this.publishBusy()) {
      return;
    }
    this.publishBusy.set(true);
    try {
      const saved = await this.save();
      if (!saved || !this.postId()) {
        return;
      }
      await this.posts.publish(this.postId()!);
      this.status.set('PUBLISHED');
      this.toasts.show('Запись опубликована', 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.publishBusy.set(false);
    }
  }

  protected async schedulePost(): Promise<void> {
    const local = this.scheduleAt();
    if (!local || this.scheduleBusy()) {
      return;
    }
    const publishAt = new Date(local).toISOString();
    this.scheduleBusy.set(true);
    try {
      const saved = await this.save();
      if (!saved || !this.postId()) {
        return;
      }
      await this.posts.schedule(this.postId()!, publishAt);
      this.status.set('SCHEDULED');
      this.scheduleFailureReason.set(null);
      this.toasts.show('Публикация отложена', 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.scheduleBusy.set(false);
    }
  }

  protected async cancelSchedule(): Promise<void> {
    if (!this.postId() || this.scheduleBusy()) {
      return;
    }
    this.scheduleBusy.set(true);
    try {
      await this.posts.cancelSchedule(this.postId()!);
      this.status.set('DRAFT');
      this.toasts.show('Расписание отменено');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.scheduleBusy.set(false);
    }
  }

  protected async removePost(): Promise<void> {
    if (!this.postId() || this.removing()) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: 'Удалить запись?',
      message: 'Запись будет недоступна по обычным ссылкам. Действие нельзя отменить из интерфейса.',
      confirmLabel: 'Удалить',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.removing.set(true);
    try {
      await this.posts.remove(this.postId()!);
      this.toasts.show('Запись удалена', 'success');
      await this.router.navigate(['/posts/drafts']);
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.removing.set(false);
    }
  }
}

function minScheduleLocal(): string {
  const at = new Date(Date.now() + 60_000);
  const offsetMs = at.getTimezoneOffset() * 60_000;
  return new Date(at.getTime() - offsetMs).toISOString().slice(0, 16);
}
