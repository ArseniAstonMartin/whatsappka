import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccountRecoveryService } from '../../../core/account-recovery.service';
import { AccountSecurityService, SessionItem } from '../../../core/account-security.service';
import { AuthService } from '../../../core/auth.service';
import { toProblem } from '../../../core/api-error';
import { ProfileService } from '../../../core/profile.service';
import { googleErrorText } from '../../auth/auth-form';
import { AppButton } from '../../../shared/ui/button/app-button';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { ToastService } from '../../../shared/ui/toast/toast.service';

/**
 * Безопасность аккаунта: сеансы, смена пароля, привязка Google. Всё, что меняет доступ, выполняется по явному действию
 * и подтверждается ответом сервера; токены и их значения на странице не показываются.
 */
@Component({
  selector: 'app-security-settings',
  imports: [AppButton, RouterLink, StatePanel, Skeleton, DatePipe],
  templateUrl: './security-settings.html',
  styleUrl: './security-settings.scss',
})
export class SecuritySettings implements OnInit {
  private readonly sessionsApi = inject(AccountSecurityService);
  private readonly recovery = inject(AccountRecoveryService);
  private readonly profiles = inject(ProfileService);
  private readonly auth = inject(AuthService);
  private readonly confirm = inject(ConfirmService);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly sessions = signal<SessionItem[]>([]);
  protected readonly sessionsLoading = signal(true);
  protected readonly sessionsError = signal<string | null>(null);
  protected readonly pendingSessionIds = signal<ReadonlySet<string>>(new Set());
  protected readonly email = signal<string | null>(null);
  protected readonly resetSending = signal(false);
  protected readonly googleEnabled = signal<boolean | null>(null);
  protected readonly googleBusy = signal(false);
  protected readonly googleNotice = signal<string | null>(null);
  protected readonly googleError = signal<string | null>(null);

  ngOnInit(): void {
    void this.loadSessions();
    void this.loadEmail();
    void this.loadProviders();
    this.readGoogleResult();
  }

  protected async loadSessions(): Promise<void> {
    this.sessionsLoading.set(true);
    this.sessionsError.set(null);
    try {
      this.sessions.set(await this.sessionsApi.sessions());
    } catch (error) {
      this.sessionsError.set(toProblem(error).message);
    } finally {
      this.sessionsLoading.set(false);
    }
  }

  protected isPending(id: string): boolean {
    return this.pendingSessionIds().has(id);
  }

  /** Текущий сеанс завершает вход: сервер отзывает сессию, интерфейс возвращается на страницу входа. */
  protected async revoke(session: SessionItem): Promise<void> {
    if (this.isPending(session.id)) {
      return;
    }
    if (session.current) {
      await this.auth.logout();
      return;
    }
    this.pendingSessionIds.update((ids) => new Set([...ids, session.id]));
    try {
      await this.sessionsApi.revokeSession(session.id);
      this.sessions.update((list) => list.filter((item) => item.id !== session.id));
      this.toasts.show('Сеанс завершён', 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.pendingSessionIds.update((ids) => new Set([...ids].filter((id) => id !== session.id)));
    }
  }

  protected async logoutAll(): Promise<void> {
    const confirmed = await this.confirm.confirm({
      title: 'Завершить все сеансы?',
      message: 'Все устройства, включая это, придётся войти заново.',
      confirmLabel: 'Завершить все',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    try {
      await this.sessionsApi.logoutAll();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
      return;
    }
    // Сервер уже отозвал все сеансы и очистил cookie: здесь только очищаем вкладку и уходим на вход.
    await this.auth.logout();
  }

  /** Письмо со ссылкой — единственный путь смены пароля: старые сеансы отзываются после выполнения ссылки. */
  protected async sendPasswordReset(): Promise<void> {
    const address = this.email();
    if (!address || this.resetSending()) {
      return;
    }
    this.resetSending.set(true);
    try {
      await this.recovery.requestPasswordReset(address);
      this.toasts.show('Письмо со ссылкой отправлено. Проверьте почту.', 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.resetSending.set(false);
    }
  }

  protected async linkGoogle(): Promise<void> {
    if (this.googleBusy()) {
      return;
    }
    this.googleBusy.set(true);
    this.googleError.set(null);
    try {
      const url = await this.sessionsApi.startGoogleLink();
      // Полная навигация к Google: возврат придёт на эту страницу с результатом, без токенов в адресе.
      window.location.assign(url);
    } catch (error) {
      this.googleError.set(toProblem(error).message);
      this.googleBusy.set(false);
    }
  }

  private async loadEmail(): Promise<void> {
    try {
      this.email.set((await this.profiles.own()).email);
    } catch {
      this.email.set(null);
    }
  }

  private async loadProviders(): Promise<void> {
    try {
      this.googleEnabled.set((await this.recovery.providers()).google);
    } catch {
      this.googleEnabled.set(false);
    }
  }

  /** Результат привязки возвращает сюда через адрес: только код результата, без токенов. */
  private readGoogleResult(): void {
    const params = this.route.snapshot.queryParamMap;
    if (params.get('google') === 'linked') {
      this.googleNotice.set('Аккаунт Google привязан. Теперь можно входить через него.');
    }
    const error = params.get('error');
    if (error) {
      this.googleError.set(googleErrorText(error));
    }
    if (params.keys.length) {
      void this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    }
  }
}
