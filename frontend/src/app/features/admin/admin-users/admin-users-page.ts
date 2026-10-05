import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AdminUser, AdminUsersService, StaffRole } from '../../../core/admin-users.service';
import { toProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { ToastService } from '../../../shared/ui/toast/toast.service';

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
    await this.run(async () => {
      const result = await this.api.setRoles(user.id, this.draftRoles(), this.reason().trim());
      this.applyLocal({ ...user, roles: ['USER', ...result.roles] });
    }, 'Роли сохранены.');
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
      this.actionError.set(toProblem(error).message);
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
