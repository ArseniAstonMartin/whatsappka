import { Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { REPORT_REASONS, ReportReason, ReportService, ReportTargetKind, reportFailureText } from '../../core/report.service';
import { AppButton } from '../ui/button/app-button';

export interface ReportDialogData {
  targetKind: ReportTargetKind;
  targetId: string;
}

const DESCRIPTION_MAX = 2000;

/**
 * Общий диалог причин жалобы. Закрывается с true только после успешной отправки. Отправляет одну явную цель:
 * вид и id, без текста объекта. Ограничение «одна открытая жалоба» показано заранее, а не только при ошибке.
 */
@Component({
  selector: 'app-report-dialog',
  imports: [AppButton],
  templateUrl: './report-dialog.html',
  styleUrl: './report-dialog.scss',
})
export class ReportDialog {
  private readonly ref = inject<DialogRef<boolean>>(DialogRef);
  private readonly data = inject<ReportDialogData>(DIALOG_DATA);
  private readonly reports = inject(ReportService);

  protected readonly reasons = REPORT_REASONS;
  protected readonly maxLength = DESCRIPTION_MAX;
  protected readonly reason = signal<ReportReason | null>(null);
  protected readonly description = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected select(value: ReportReason): void {
    this.reason.set(value);
    this.error.set(null);
  }

  protected async submit(): Promise<void> {
    const reason = this.reason();
    if (!reason) {
      this.error.set('Выберите причину жалобы.');
      return;
    }
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const text = this.description().trim();
      await this.reports.submit(this.data.targetKind, this.data.targetId, reason, text.length > 0 ? text : null);
      this.ref.close(true);
    } catch (error) {
      this.error.set(reportFailureText(error));
    } finally {
      this.busy.set(false);
    }
  }

  protected cancel(): void {
    this.ref.close(false);
  }
}
