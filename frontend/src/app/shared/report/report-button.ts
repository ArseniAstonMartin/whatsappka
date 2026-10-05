import { Component, inject, input } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { ReportDialog, ReportDialogData } from './report-dialog';
import { ReportTargetKind } from '../../core/report.service';
import { AppButton } from '../ui/button/app-button';
import { ToastService } from '../ui/toast/toast.service';

/** Открывает диалог причин. Результат — тост; сам диалог отправляет только вид и id цели. */
export function openReport(dialog: Dialog, toasts: ToastService, targetKind: ReportTargetKind, targetId: string): void {
  const data: ReportDialogData = { targetKind, targetId };
  const ref = dialog.open<boolean>(ReportDialog, {
    data,
    ariaLabelledBy: 'report-title',
    autoFocus: 'first-tabbable',
    restoreFocus: true,
  });
  ref.closed.subscribe((sent) => {
    if (sent === true) {
      toasts.show('Жалоба отправлена. Модераторы её рассмотрят.', 'success');
    }
  });
}

/** Действие «Пожаловаться». Ставится только там, где объект уже виден зрителю. */
@Component({
  selector: 'app-report-button',
  imports: [AppButton],
  template: `<button type="button" appButton variant="ghost" (click)="open()">{{ label() }}</button>`,
})
export class ReportButton {
  private readonly dialog = inject(Dialog);
  private readonly toasts = inject(ToastService);

  readonly targetKind = input.required<ReportTargetKind>();
  readonly targetId = input.required<string>();
  readonly label = input('Пожаловаться');

  protected open(): void {
    openReport(this.dialog, this.toasts, this.targetKind(), this.targetId());
  }
}
