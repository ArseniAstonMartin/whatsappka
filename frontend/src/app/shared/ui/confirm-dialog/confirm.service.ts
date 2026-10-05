import { Injectable, inject } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { ConfirmDialog, ConfirmOptions } from './confirm-dialog';

/** Открывает подтверждение и возвращает решение пользователя. Закрытие Esc или кнопкой «Отмена» даёт false. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(Dialog);

  confirm(options: ConfirmOptions): Promise<boolean> {
    const ref = this.dialog.open<boolean>(ConfirmDialog, {
      data: options,
      ariaLabelledBy: 'confirm-title',
      ariaDescribedBy: 'confirm-message',
      autoFocus: 'first-tabbable',
      restoreFocus: true,
    });
    return new Promise<boolean>((resolve) => {
      ref.closed.subscribe((result) => resolve(result === true));
    });
  }
}
