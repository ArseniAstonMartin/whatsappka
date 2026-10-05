import { Component, DestroyRef, OnDestroy, computed, inject, input, output, signal } from '@angular/core';
import { HttpClient, HttpEventType } from '@angular/common/http';
import { Subscription, firstValueFrom, lastValueFrom } from 'rxjs';
import { AppButton } from '../../ui/button/app-button';
import { AppIcon } from '../../icon/app-icon';
import { toProblem } from '../../../core/api-error';
import { FAILURE_TEXT, MediaPurpose, PURPOSE_RULES } from '../purposes';

export interface UploadedMedia {
  id: string;
  purpose: MediaPurpose;
  filename: string;
  sizeBytes: number;
}

type Phase = 'idle' | 'uploading' | 'processing' | 'ready' | 'failed';

interface UploadResponse {
  id: string;
  status: string;
  purpose: MediaPurpose;
  mime: string;
  sizeBytes: number;
  filename: string;
}

interface StatusResponse {
  id: string;
  status: 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED';
  failureCode: string | null;
}

const POLL_INTERVAL_MS = 1000;
const POLL_LIMIT = 90;

/**
 * Загрузка файла с прогрессом и ожиданием обработки. Сообщает о файле только после READY:
 * привязка к объекту допустима лишь для готового файла.
 */
@Component({
  selector: 'app-media-upload',
  imports: [AppButton, AppIcon],
  templateUrl: './media-upload.html',
  styleUrl: './media-upload.scss',
})
export class MediaUpload implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  readonly purpose = input.required<MediaPurpose>();
  /** Вызывается, когда файл готов (READY). Привязку выполняет родитель. */
  readonly ready = output<UploadedMedia>();
  readonly removed = output<string>();

  protected readonly rules = computed(() => PURPOSE_RULES[this.purpose()]);
  protected readonly phase = signal<Phase>('idle');
  protected readonly progress = signal(0);
  protected readonly media = signal<UploadResponse | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly failureText = signal<string | null>(null);
  protected readonly busy = computed(() => this.phase() === 'uploading' || this.phase() === 'processing');

  private upload: Subscription | null = null;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private polls = 0;
  private stopped = false;

  constructor() {
    this.destroyRef.onDestroy(() => this.stop());
  }

  ngOnDestroy(): void {
    this.stop();
  }

  protected onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) {
      this.start(file);
    }
  }

  protected start(file: File): void {
    this.message.set(null);
    const rules = this.rules();
    if (file.size === 0) {
      this.message.set('Файл пустой');
      return;
    }
    if (file.size > rules.maxBytes) {
      this.message.set(`Файл больше допустимого. Нужно: ${rules.allowedText}`);
      return;
    }
    if (rules.accept.split(',').indexOf(file.type) === -1) {
      this.message.set(`Этот формат не подходит. Нужно: ${rules.allowedText}`);
      return;
    }
    this.phase.set('uploading');
    this.progress.set(0);
    this.failureText.set(null);
    this.stopped = false;
    this.upload = this.http
      .post<UploadResponse>(`/api/v1/media?purpose=${this.purpose()}`, file, {
        headers: {
          'Content-Type': file.type,
          'X-Filename': encodeURIComponent(file.name),
        },
        reportProgress: true,
        observe: 'events',
      })
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.progress.set(Math.round((event.loaded / event.total) * 100));
          } else if (event.type === HttpEventType.Response && event.body) {
            this.media.set(event.body);
            this.phase.set('processing');
            this.schedulePoll();
          }
        },
        error: (error: unknown) => this.fail(toProblem(error).message),
      });
  }

  /** Отмена до готовности: передача прерывается, готовый, но не привязанный файл удаляется. */
  protected cancel(): void {
    const current = this.media();
    this.stop();
    if (current) {
      void this.deleteRemote(current.id);
    }
    this.reset();
  }

  protected async remove(): Promise<void> {
    const current = this.media();
    if (!current) {
      return;
    }
    const ok = await this.deleteRemote(current.id);
    if (ok) {
      this.reset();
      this.removed.emit(current.id);
    }
  }

  private async deleteRemote(id: string): Promise<boolean> {
    try {
      await lastValueFrom(this.http.delete(`/api/v1/media/${id}`));
      return true;
    } catch (error) {
      this.message.set(toProblem(error).message);
      return false;
    }
  }

  private schedulePoll(): void {
    this.pollTimer = setTimeout(() => void this.poll(), POLL_INTERVAL_MS);
  }

  private async poll(): Promise<void> {
    this.pollTimer = null;
    const current = this.media();
    if (this.stopped || !current) {
      return;
    }
    try {
      const status = await firstValueFrom(this.http.get<StatusResponse>(`/api/v1/media/${current.id}/status`));
      if (this.stopped) {
        return;
      }
      if (status.status === 'READY') {
        this.phase.set('ready');
        this.ready.emit({
          id: current.id,
          purpose: current.purpose,
          filename: current.filename,
          sizeBytes: current.sizeBytes,
        });
        return;
      }
      if (status.status === 'FAILED') {
        this.fail(FAILURE_TEXT[status.failureCode ?? ''] ?? 'Не удалось обработать файл');
        return;
      }
      this.polls += 1;
      if (this.polls >= POLL_LIMIT) {
        this.fail('Обработка занимает слишком долго. Попробуйте позже.');
        return;
      }
      this.schedulePoll();
    } catch (error) {
      this.fail(toProblem(error).message);
    }
  }

  private fail(text: string): void {
    this.phase.set('failed');
    this.failureText.set(text);
    this.stopPolling();
    this.upload?.unsubscribe();
    this.upload = null;
  }

  private stop(): void {
    this.stopped = true;
    this.upload?.unsubscribe();
    this.upload = null;
    this.stopPolling();
  }

  private stopPolling(): void {
    if (this.pollTimer !== null) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  }

  protected resetAndPick(): void {
    this.reset();
  }

  private reset(): void {
    this.phase.set('idle');
    this.progress.set(0);
    this.media.set(null);
    this.polls = 0;
    this.failureText.set(null);
  }
}
