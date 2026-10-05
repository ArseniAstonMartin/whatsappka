import { Component, inject } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { AppButton } from '../button/app-button';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Необратимое действие, например удаление: кнопка подтверждения становится опасной. */
  danger?: boolean;
}

/** Содержимое подтверждения. Фокус держит CDK Dialog, Esc закрывает без действия. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [AppButton],
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.scss',
})
export class ConfirmDialog {
  protected readonly options = inject<ConfirmOptions>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<boolean>>(DialogRef);

  protected cancel(): void {
    this.ref.close(false);
  }

  protected confirm(): void {
    this.ref.close(true);
  }
}
