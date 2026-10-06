import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AdminAccess } from '../../../core/admin-access.service';
import { AuditEntry, AuditService } from '../../../core/audit.service';
import { CurrentUser } from '../../../core/current-user';
import { toProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';

const ACTION_LABELS: Record<string, string> = {
  REPORT_TAKEN: 'Жалоба взята в работу',
  REPORT_RESOLVED: 'Жалоба решена',
  REPORT_REJECTED: 'Жалоба отклонена',
  CONTENT_HIDDEN: 'Контент скрыт',
  CONTENT_RESTORED: 'Контент восстановлен',
  SANCTION_ISSUED: 'Блокировка выдана',
  SANCTION_LIFTED: 'Блокировка снята',
  ROLE_GRANTED: 'Роль выдана',
  ROLE_REVOKED: 'Роль снята',
  USER_VERIFIED: 'Пользователь проверен',
  USER_UNVERIFIED: 'Проверка снята',
  APP_SETTING_CHANGED: 'Настройка изменена',
  JOB_RETRIED: 'Задание перезапущено',
  BOOTSTRAP_ADMIN_CREATED: 'Создан первый администратор',
};

/** Действия, которые модератор видит в собственном журнале (совпадает с сервером). */
const MODERATOR_ACTIONS = ['REPORT_TAKEN', 'REPORT_RESOLVED', 'REPORT_REJECTED', 'CONTENT_HIDDEN', 'SANCTION_ISSUED'];

const TARGET_TYPES = ['user', 'report', 'post', 'comment', 'group', 'message', 'app_setting', 'background_job'];

/**
 * Журнал аудита только для чтения. Модератор видит свои разрешённые решения, администратор — всё.
 * Записи нельзя изменить или удалить ни здесь, ни через API.
 */
@Component({
  selector: 'app-audit-page',
  imports: [AppButton, DatePipe, Skeleton, StatePanel],
  templateUrl: './audit-page.html',
  styleUrl: './audit-page.scss',
})
export class AuditPage implements OnInit {
  private readonly api = inject(AuditService);
  private readonly access = inject(AdminAccess);
  private readonly user = inject(CurrentUser);

  protected readonly isAdmin = computed(() => this.user.hasAnyRole(['ADMIN']));
  protected readonly actions = computed(() => (this.isAdmin() ? Object.keys(ACTION_LABELS) : MODERATOR_ACTIONS));
  protected readonly targetTypes = TARGET_TYPES;
  protected readonly action = signal<string | null>(null);
  protected readonly targetType = signal<string | null>(null);
  protected readonly items = signal<AuditEntry[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);

  private token = 0;

  ngOnInit(): void {
    void this.load(true);
  }

  protected label(action: string): string {
    return ACTION_LABELS[action] ?? action;
  }

  protected async apply(): Promise<void> {
    await this.load(true);
  }

  protected async load(reset: boolean): Promise<void> {
    const token = ++this.token;
    if (reset) {
      this.loading.set(true);
      this.error.set(null);
    } else {
      this.loadingMore.set(true);
    }
    try {
      const page = await this.api.list({
        action: this.action(),
        targetType: this.targetType(),
        cursor: reset ? null : this.nextCursor(),
      });
      if (token !== this.token) {
        return;
      }
      this.items.set(reset ? page.items : [...this.items(), ...page.items]);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
    } catch (error) {
      if (this.access.handleError(error)) {
        return;
      }
      if (token === this.token) {
        this.error.set(toProblem(error).message);
      }
    } finally {
      if (token === this.token) {
        this.loading.set(false);
        this.loadingMore.set(false);
      }
    }
  }
}
