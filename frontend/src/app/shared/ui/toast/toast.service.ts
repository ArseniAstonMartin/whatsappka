import { Injectable, signal } from '@angular/core';

export type ToastKind = 'info' | 'success' | 'error';

export interface Toast {
  id: number;
  message: string;
  kind: ToastKind;
}

const DEFAULT_DURATION_MS = 5000;
let nextToastId = 0;

/** Короткие уведомления. Ошибки не закрываются сами: пользователь должен их увидеть. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly items = signal<readonly Toast[]>([]);

  readonly toasts = this.items.asReadonly();

  show(message: string, kind: ToastKind = 'info'): number {
    const id = nextToastId++;
    this.items.update((current) => [...current, { id, message, kind }]);
    if (kind !== 'error') {
      setTimeout(() => this.dismiss(id), DEFAULT_DURATION_MS);
    }
    return id;
  }

  dismiss(id: number): void {
    this.items.update((current) => current.filter((toast) => toast.id !== id));
  }
}
