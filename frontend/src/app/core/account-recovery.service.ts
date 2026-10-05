import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export interface AuthProviders {
  google: boolean;
}

/** Публичные сценарии входа и восстановления: способы входа, сброс пароля, подтверждение почты. */
@Injectable({ providedIn: 'root' })
export class AccountRecoveryService {
  private readonly http = inject(HttpClient);

  providers(): Promise<AuthProviders> {
    return lastValueFrom(this.http.get<AuthProviders>('/api/v1/auth/providers'));
  }

  /** Ответ одинаков для любого адреса: по нему нельзя понять, есть ли аккаунт. */
  requestPasswordReset(email: string): Promise<void> {
    return lastValueFrom(this.http.post<void>('/api/v1/auth/password-reset/request', { email }));
  }

  confirmPasswordReset(token: string, password: string): Promise<void> {
    return lastValueFrom(this.http.post<void>('/api/v1/auth/password-reset/confirm', { token, password }));
  }

  confirmEmail(token: string): Promise<void> {
    return lastValueFrom(this.http.post<void>('/api/v1/auth/email-confirmation/confirm', { token }));
  }
}
