import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminAccess } from '../../../core/admin-access.service';
import { AdminStats, AdminStatsService } from '../../../core/admin-stats.service';
import { toProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';

/** Дата в UTC в формате ГГГГ-ММ-ДД: так же её понимает сервер. */
function utcDate(daysAgo: number): string {
  const d = new Date(Date.now() - daysAgo * 86_400_000);
  return d.toISOString().slice(0, 10);
}

/**
 * Агрегаты за выбранный период. Только числа из базы: без графиков, которых сервер не отдаёт.
 * DAU — сколько пользователей авторизованно обращались к сервису в последний день периода (UTC).
 */
@Component({
  selector: 'app-admin-stats-page',
  imports: [FormsModule, AppButton, Skeleton, StatePanel],
  templateUrl: './admin-stats-page.html',
  styleUrl: './admin-stats-page.scss',
})
export class AdminStatsPage implements OnInit {
  private readonly api = inject(AdminStatsService);
  private readonly access = inject(AdminAccess);

  protected readonly from = signal(utcDate(6));
  protected readonly to = signal(utcDate(0));
  protected readonly maxDate = utcDate(0);
  protected readonly stats = signal<AdminStats | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.show();
  }

  protected async show(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.stats.set(await this.api.get(this.from(), this.to()));
    } catch (error) {
      if (!this.access.handleError(error)) {
        this.stats.set(null);
        this.error.set(toProblem(error).message);
      }
    } finally {
      this.loading.set(false);
    }
  }

  protected mib(bytes: number): string {
    return (bytes / (1024 * 1024)).toFixed(1);
  }
}
