import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AdminUser, AdminUsersService, StaffRole } from '../../../core/admin-users.service';
import { AdminAccess } from '../../../core/admin-access.service';
import { ModerationService } from '../../../core/moderation.service';
import { toProblem } from '../../../core/api-error';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { ToastService } from '../../../shared/ui/toast/toast.service';

type UserFilter = 'all' | 'verified' | 'staff' | 'restricted';

const FILTERS: readonly { value: UserFilter; label: string }[] = [
  { value: 'all', label: 'Все' },
  { value: 'verified', label: 'Проверенные' },
  { value: 'staff', label: 'С служебной ролью' },
  { value: 'restricted', label: 'Ограничены' },
];

const SANCTION_HOURS: readonly { hours: number; label: string }[] = [
  { hours: 24, label: '1 день' },
  { hours: 72, label: '3 дня' },
  { hours: 168, label: '7 дней' },
];

const STAFF: readonly { role: StaffRole; label: string; hint: string }[] = [
  { role: 'MODERATOR', label: 'Модератор', hint: 'Очередь жалоб, решения, временные блокировки' },
  { role: 'ADMIN', label: 'Администратор', hint: 'Всё, включая роли, верификацию и постоянные блокировки' },
];

/**
 * Пользователи и роли. Изменения требуют причины и записываются в аудит на сервере.
 * Роль USER есть у всех и здесь не меняется. Последнего активного администратора сервер не пускает.
 */
@Component({
  selector: 'app-admin-users-page',
  imports: [AppButton, Skeleton, StatePanel],
  templateUrl: './admin-users-page.html',
  styleUrl: './admin-users-page.scss',
})
export class AdminUsersPage implements OnInit {
  private readonly api = inject(AdminUsersService);
  private readonly toasts = inject(ToastService);
  private readonly access = inject(AdminAccess);
  private readonly confirm = inject(ConfirmService);
  private readonly moderation = inject(ModerationService);

  protected readonly filters = FILTERS;
  protected readonly sanctionOptions = SANCTION_HOURS;
  protected readonly filter = signal<UserFilter>('all');
  protected readonly sanctionHours = signal(24);

  protected readonly staff = STAFF;
  protected readonly query = signal('');
  protected readonly items = signal<AdminUser[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly selected = signal<AdminUser | null>(null);
  /** Черновик ролей: отмечается в форме, сохраняется только кнопкой. */
  protected readonly draftRoles = signal<StaffRole[]>([]);
  protected readonly reason = signal('');
  protected readonly busy = signal(false);
  protected readonly actionError = signal<string | null>(null);

  /** Фильтр работает по уже загруженной странице; серверный поиск остаётся по логину и имени. */
  protected readonly visible = computed(() => {
    const f = this.filter();
    return this.items().filter((u) => {
      switch (f) {
        case 'verified':
          return u.verified;
        case 'staff':
          return u.roles.some((r) => r === 'MODERATOR' || r === 'ADMIN');
        case 'restricted':
          return u.status === 'SUSPENDED';
        default:
          return true;
      }
    });
  });

  protected readonly rolesChanged = computed(() => {
    const s = this.selected();
    if (!s) {
      return false;
    }
    const current = s.roles.filter((r): r is StaffRole => r === 'MODERATOR' || r === 'ADMIN').sort();
    return current.join() !== [...this.draftRoles()].sort().join();
  });

  private token = 0;

  ngOnInit(): void {
    void this.load(true);
  }

  protected async search(value: string): Promise<void> {
    this.query.set(value.trim());
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
      const page = await this.api.list(this.query(), reset ? null : this.nextCursor());
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
        if (reset) {
          this.error.set(toProblem(error).message);
        } else {
          this.toasts.show(toProblem(error).message, 'error');
        }
      }
    } finally {
      if (token === this.token) {
        this.loading.set(false);
        this.loadingMore.set(false);
      }
    }
  }

  protected select(user: AdminUser): void {
    this.selected.set(user);
    this.draftRoles.set(user.roles.filter((r): r is StaffRole => r === 'MODERATOR' || r === 'ADMIN'));
    this.reason.set('');
    this.actionError.set(null);
  }

  protected toggleRole(role: StaffRole, on: boolean): void {
    const current = this.draftRoles().filter((r) => r !== role);
    this.draftRoles.set(on ? [...current, role] : current);
  }

  protected hasDraft(role: StaffRole): boolean {
    return this.draftRoles().includes(role);
  }

  protected async saveRoles(): Promise<void> {
    const user = this.selected();
    if (!user || !this.requireReason()) {
      return;
    }
    const grantsOrRevokesAdmin = user.roles.includes('ADMIN') !== this.draftRoles().includes('ADMIN');
    if (grantsOrRevokesAdmin) {
      const grant = this.draftRoles().includes('ADMIN');
      const ok = await this.confirm.confirm({
        title: grant ? 'Выдать роль администратора?' : 'Снять роль администратора?',
        message: grant
          ? 'Администратор получает доступ ко всем служебным разделам, включая блокировки и настройки.'
          : 'Пользователь потеряет доступ к служебным разделам сразу после этого действия.',
        confirmLabel: grant ? 'Выдать' : 'Снять',
        danger: true,
      });
      if (!ok) {
        return;
      }
    }
    await this.run(async () => {
      const result = await this.api.setRoles(user.id, this.draftRoles(), this.reason().trim());
      this.applyLocal({ ...user, roles: ['USER', ...result.roles] });
    }, 'Роли сохранены.');
  }

  /** Временная блокировка: модератор и администратор. Срок выбирается из заданного набора. */
  protected async sanctionTemporary(): Promise<void> {
    const user = this.selected();
    if (!user || !this.requireReason()) {
      return;
    }
    const hours = this.sanctionHours();
    const label = this.sanctionOptions.find((o) => o.hours === hours)?.label ?? `${hours} ч`;
    const ok = await this.confirm.confirm({
      title: `Ограничить аккаунт на ${label}?`,
      message: 'Пользователь не сможет входить и публиковать, пока срок не истечёт. Причина попадёт в аудит.',
      confirmLabel: 'Ограничить',
      danger: true,
    });
    if (!ok) {
      return;
    }
    await this.run(async () => {
      await this.moderation.temporarySanction(user.id, hours, this.reason().trim());
      this.applyLocal({ ...user, status: 'SUSPENDED' });
    }, 'Аккаунт ограничен.');
  }

  /** Постоянная блокировка — только администратор (страница доступна только ему). */
  protected async sanctionPermanent(): Promise<void> {
    const user = this.selected();
    if (!user || !this.requireReason()) {
      return;
    }
    const ok = await this.confirm.confirm({
      title: 'Заблокировать навсегда?',
      message: 'Снять постоянную блокировку может только администратор, и только с причиной.',
      confirmLabel: 'Заблокировать навсегда',
      danger: true,
    });
    if (!ok) {
      return;
    }
    await this.run(async () => {
      await this.moderation.permanentSanction(user.id, this.reason().trim());
      this.applyLocal({ ...user, status: 'SUSPENDED' });
    }, 'Аккаунт заблокирован.');
  }

  protected async toggleVerified(): Promise<void> {
    const user = this.selected();
    if (!user || !this.requireReason()) {
      return;
    }
    const next = !user.verified;
    await this.run(async () => {
      const result = await this.api.setVerified(user.id, next, this.reason().trim());
      this.applyLocal({ ...user, verified: result.verified });
    }, next ? 'Верификация выставлена.' : 'Верификация снята.');
  }

  private requireReason(): boolean {
    if (this.reason().trim().length === 0) {
      this.actionError.set('Укажите причину: она попадёт в аудит.');
      return false;
    }
    return true;
  }

  private async run(action: () => Promise<void>, success: string): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    try {
      await action();
      this.reason.set('');
      this.toasts.show(success, 'success');
    } catch (error) {
      if (!this.access.handleError(error)) {
        this.actionError.set(toProblem(error).message);
      }
    } finally {
      this.busy.set(false);
    }
  }

  private applyLocal(updated: AdminUser): void {
    this.selected.set(updated);
    this.draftRoles.set(updated.roles.filter((r): r is StaffRole => r === 'MODERATOR' || r === 'ADMIN'));
    this.items.set(this.items().map((u) => (u.id === updated.id ? updated : u)));
  }
}
