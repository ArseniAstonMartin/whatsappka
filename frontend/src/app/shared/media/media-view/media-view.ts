import { Component, DestroyRef, OnDestroy, effect, inject, input, signal, untracked } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AppButton } from '../../ui/button/app-button';
import { AppIcon } from '../../icon/app-icon';
import { Skeleton } from '../../ui/skeleton/skeleton';
import { toProblem } from '../../../core/api-error';

/**
 * Просмотр файла. Байты запрашиваются с текущей авторизацией (заголовок Bearer ставит interceptor),
 * адрес для показа — временный object URL, который освобождается при смене файла и при уничтожении.
 */
@Component({
  selector: 'app-media-view',
  imports: [AppButton, AppIcon, Skeleton],
  templateUrl: './media-view.html',
  styleUrl: './media-view.scss',
})
export class MediaView implements OnDestroy {
  private readonly http = inject(HttpClient);

  readonly mediaId = input.required<string>();
  /** Размер варианта для изображений; null — оригинал. */
  readonly variant = input<'320' | '1280' | null>(null);
  readonly kind = input<'image' | 'document'>('image');
  readonly label = input.required<string>();

  protected readonly objectUrl = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  private request: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.release());
    effect(() => {
      const id = this.mediaId();
      const variant = this.variant();
      untracked(() => this.load(id, variant));
    });
  }

  ngOnDestroy(): void {
    this.release();
  }

  protected download(): void {
    const url = this.objectUrl();
    if (!url) {
      return;
    }
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = this.label();
    anchor.click();
  }

  private load(id: string, variant: string | null): void {
    this.release();
    this.loading.set(true);
    this.error.set(null);
    const path = variant ? `/api/v1/media/${id}/variants/${variant}` : `/api/v1/media/${id}/content`;
    this.request = this.http.get(path, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.objectUrl.set(URL.createObjectURL(blob));
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(toProblem(error).message);
      },
    });
  }

  private release(): void {
    this.request?.unsubscribe();
    this.request = null;
    const url = this.objectUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
    this.objectUrl.set(null);
  }
}
