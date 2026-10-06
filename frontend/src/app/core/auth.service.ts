import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { lastValueFrom } from 'rxjs';
import { CurrentUser } from './current-user';

export interface SessionUser {
  id: string;
  username: string;
  roles: string[];
}

export type AuthStatus = 'unknown' | 'authenticated' | 'guest';

interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: { id: string; username: string };
}

interface MeResponse {
  id: string;
  username: string;
  roles: string[];
}

const CHANNEL_NAME = 'whatsappka-auth';
const REFRESH_LOCK = 'whatsappka-refresh';

/**
 * Состояние входа. Access-токен только в памяти: ни localStorage, ни адрес страницы.
 * Refresh-токен живёт в HttpOnly cookie, его читает только сервер.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly currentUser = inject(CurrentUser);

  private accessToken: string | null = null;
  private readonly userState = signal<SessionUser | null>(null);
  private readonly statusState = signal<AuthStatus>('unknown');
  private refreshing: Promise<boolean> | null = null;
  private readonly channel: BroadcastChannel | null =
    typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL_NAME);

  readonly user = this.userState.asReadonly();
  readonly status = this.statusState.asReadonly();

  constructor() {
    // Выход в одной вкладке отключает и остальные: их токены в памяти больше не нужны.
    this.channel?.addEventListener('message', (event: MessageEvent<string>) => {
      if (event.data === 'logout') {
        this.forgetLocally();
      }
    });
  }

  /** Access-токен для заголовка Authorization. Null — гость или сессия ещё не восстановлена. */
  token(): string | null {
    return this.accessToken;
  }

  /** Вызывается до первой навигации: восстанавливает сессию по cookie после перезагрузки. */
  async restore(): Promise<void> {
    await this.refresh();
  }

  /** Перечитывает роли с сервера: после потери роли интерфейс должен закрыть служебные разделы. */
  refreshProfile(): Promise<void> {
    return this.loadProfile();
  }

  async login(email: string, password: string): Promise<void> {
    const response = await lastValueFrom(
      this.http.post<TokenResponse>('/api/v1/auth/login', { email, password }),
    );
    this.accept(response.accessToken);
    await this.loadProfile();
  }

  async register(email: string, username: string, password: string): Promise<void> {
    await lastValueFrom(this.http.post('/api/v1/auth/register', { email, username, password }));
    await this.login(email, password);
  }

  /** Обновляет access-токен по cookie. Одновременные вызовы делят один запрос, вкладки ждут друг друга. */
  refresh(): Promise<boolean> {
    if (!this.refreshing) {
      this.refreshing = this.withRefreshLock(() => this.doRefresh()).finally(() => {
        this.refreshing = null;
      });
    }
    return this.refreshing;
  }

  async logout(): Promise<void> {
    try {
      await lastValueFrom(this.http.post('/api/v1/auth/logout', {}));
    } catch {
      // Сессия могла уже истечь: локальное состояние всё равно очищаем.
    }
    this.forgetLocally();
    this.channel?.postMessage('logout');
    await this.router.navigateByUrl('/login');
  }

  /** Очищает состояние в этой вкладке без сетевых вызовов. */
  forgetLocally(): void {
    this.accessToken = null;
    this.userState.set(null);
    this.currentUser.setRoles([]);
    this.statusState.set('guest');
  }

  private async doRefresh(): Promise<boolean> {
    try {
      const response = await lastValueFrom(this.http.post<TokenResponse>('/api/v1/auth/refresh', {}));
      this.accept(response.accessToken);
      await this.loadProfile();
      return true;
    } catch {
      this.forgetLocally();
      return false;
    }
  }

  private accept(accessToken: string): void {
    this.accessToken = accessToken;
    this.statusState.set('authenticated');
  }

  private async loadProfile(): Promise<void> {
    try {
      const me = await lastValueFrom(this.http.get<MeResponse>('/api/v1/me'));
      this.currentUser.setRoles(me.roles);
      this.userState.set({ id: me.id, username: me.username, roles: me.roles });
    } catch {
      // Профиль не загрузился: сессия действует, но роли пусты, то есть интерфейс показывает только общие разделы.
      this.currentUser.setRoles([]);
    }
  }

  /** Между вкладками refresh идёт по очереди: иначе две вкладки предъявят один и тот же refresh-токен. */
  private async withRefreshLock<T>(work: () => Promise<T>): Promise<T> {
    if (typeof navigator === 'undefined' || !navigator.locks) {
      return work();
    }
    return await navigator.locks.request(REFRESH_LOCK, () => work());
  }
}
