import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminAccess } from '../../../core/admin-access.service';
import { AdminSettingsService, AppSettingKey, AppSettingsValues, AppSettingsView } from '../../../core/admin-settings.service';
import { toProblem } from '../../../core/api-error';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { ToastService } from '../../../shared/ui/toast/toast.service';

const MIB = 1024 * 1024;

interface Draft {
  registrationOpen: boolean;
  uploadMiB: number;
  quotaMiB: number;
  siteName: string;
}

const LABEL: Record<AppSettingKey, string> = {
  registration_open: 'Регистрация',
  media_upload_limit_bytes: 'Лимит одной загрузки',
  media_user_quota_bytes: 'Квота пользователя',
  site_name: 'Название сайта',
};

/**
 * Настройки приложения. Каждое изменённое поле уходит отдельным запросом по allowlist-ключу с одной причиной,
 * которая попадает в аудит. Ошибка показывается у своего поля, остальные изменения при этом не отправляются.
 */
@Component({
  selector: 'app-admin-settings-page',
  // FormsModule нужен, чтобы (ngSubmit) перехватывал отправку формы; иначе браузер перезагружает страницу.
  imports: [FormsModule, AppButton, Skeleton, StatePanel],
  templateUrl: './admin-settings-page.html',
  styleUrl: './admin-settings-page.scss',
})
export class AdminSettingsPage implements OnInit {
  private readonly api = inject(AdminSettingsService);
  private readonly access = inject(AdminAccess);
  private readonly confirm = inject(ConfirmService);
  private readonly toasts = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly saved = signal<AppSettingsValues | null>(null);
  protected readonly ceilings = signal<{ uploadMiB: number; quotaMiB: number }>({ uploadMiB: 20, quotaMiB: 256 });
  protected readonly draft = signal<Draft>({ registrationOpen: true, uploadMiB: 20, quotaMiB: 256, siteName: '' });
  protected readonly reason = signal('');
  protected readonly formError = signal<string | null>(null);
  protected readonly fieldErrors = signal<Partial<Record<AppSettingKey, string>>>({});
  /** Что изменилось после последнего сохранения: показываем человеку, а не только тост. */
  protected readonly lastChanged = signal<string[]>([]);

  /** Какие поля отличаются от сохранённых значений. Сравнение в тех же единицах, что уходят на сервер. */
  protected readonly changed = computed<AppSettingKey[]>(() => {
    const s = this.saved();
    const d = this.draft();
    if (!s) {
      return [];
    }
    const keys: AppSettingKey[] = [];
    if (d.registrationOpen !== s.registrationOpen) keys.push('registration_open');
    if (d.uploadMiB * MIB !== s.uploadLimitBytes) keys.push('media_upload_limit_bytes');
    if (d.quotaMiB * MIB !== s.userQuotaBytes) keys.push('media_user_quota_bytes');
    if (d.siteName.trim() !== s.siteName) keys.push('site_name');
    return keys;
  });

  ngOnInit(): void {
    void this.load();
  }

  protected label(key: AppSettingKey): string {
    return LABEL[key];
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.applyView(await this.api.get());
    } catch (error) {
      if (!this.access.handleError(error)) {
        this.error.set(toProblem(error).message);
      }
    } finally {
      this.loading.set(false);
    }
  }

  protected patch(part: Partial<Draft>): void {
    this.draft.set({ ...this.draft(), ...part });
    this.fieldErrors.set({});
    this.formError.set(null);
  }

  protected async save(): Promise<void> {
    if (this.busy()) {
      return;
    }
    const keys = this.changed();
    if (keys.length === 0) {
      return;
    }
    if (this.reason().trim().length === 0) {
      this.formError.set('Укажите причину: она попадёт в аудит.');
      return;
    }
    if (keys.includes('registration_open') && !this.draft().registrationOpen) {
      const ok = await this.confirm.confirm({
        title: 'Закрыть регистрацию?',
        message: 'Новые аккаунты не смогут зарегистрироваться ни паролем, ни через Google. Существующие пользователи входят как прежде.',
        confirmLabel: 'Закрыть регистрацию',
        danger: true,
      });
      if (!ok) {
        return;
      }
    }
    this.busy.set(true);
    this.formError.set(null);
    const reason = this.reason().trim();
    const done: AppSettingKey[] = [];
    try {
      for (const key of keys) {
        const values = await this.api.update(key, this.valueFor(key), reason);
        done.push(key);
        this.saved.set(values);
      }
      this.lastChanged.set(done.map((k) => this.describe(k)));
      this.reason.set('');
      this.applyView(await this.api.get());
      this.toasts.show('Настройки сохранены.', 'success');
    } catch (error) {
      if (this.access.handleError(error)) {
        return;
      }
      const failed = keys[done.length];
      const problem = toProblem(error);
      if (failed) {
        this.fieldErrors.set({ [failed]: problem.fields['value'] ?? problem.message });
      }
      this.formError.set(`Сохранено: ${done.length} из ${keys.length}. ${problem.message}`);
      if (done.length > 0) {
        this.lastChanged.set(done.map((k) => this.describe(k)));
        this.applyView(await this.api.get().catch(() => null) ?? null);
      }
    } finally {
      this.busy.set(false);
    }
  }

  private valueFor(key: AppSettingKey): string | boolean {
    const d = this.draft();
    switch (key) {
      case 'registration_open':
        return d.registrationOpen;
      case 'media_upload_limit_bytes':
        return String(Math.round(d.uploadMiB * MIB));
      case 'media_user_quota_bytes':
        return String(Math.round(d.quotaMiB * MIB));
      case 'site_name':
        return d.siteName.trim();
    }
  }

  private describe(key: AppSettingKey): string {
    const v = this.saved();
    if (!v) {
      return LABEL[key];
    }
    switch (key) {
      case 'registration_open':
        return `${LABEL[key]}: ${v.registrationOpen ? 'открыта' : 'закрыта'}`;
      case 'media_upload_limit_bytes':
        return `${LABEL[key]}: ${v.uploadLimitBytes / MIB} МиБ`;
      case 'media_user_quota_bytes':
        return `${LABEL[key]}: ${v.userQuotaBytes / MIB} МиБ`;
      case 'site_name':
        return `${LABEL[key]}: ${v.siteName}`;
    }
  }

  private applyView(view: AppSettingsView | null): void {
    if (!view) {
      return;
    }
    this.saved.set(view.values);
    this.ceilings.set({
      uploadMiB: view.uploadCeilingBytes / MIB,
      quotaMiB: view.quotaCeilingBytes / MIB,
    });
    this.draft.set({
      registrationOpen: view.values.registrationOpen,
      uploadMiB: view.values.uploadLimitBytes / MIB,
      quotaMiB: view.values.userQuotaBytes / MIB,
      siteName: view.values.siteName,
    });
  }
}
