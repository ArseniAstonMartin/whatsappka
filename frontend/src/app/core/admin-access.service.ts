import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { CurrentUser } from './current-user';
import { toProblem } from './api-error';
import { RealtimeService } from './realtime/realtime.service';

/**
 * Доступ к служебным экранам. Роли на сервере читаются на каждом запросе, поэтому после потери роли
 * первый же отказ 403 или переподключение realtime (сервер закрывает соединения при смене ролей)
 * перечитывают профиль и уводят со служебного экрана. Интерфейс лишь отражает решение сервера.
 */
@Injectable({ providedIn: 'root' })
export class AdminAccess {
  private readonly auth = inject(AuthService);
  private readonly user = inject(CurrentUser);
  private readonly router = inject(Router);
  private readonly realtime = inject(RealtimeService);

  constructor() {
    this.realtime.resync$.subscribe(() => void this.recheck());
  }

  /** Возвращает true, если ошибка означает отказ по роли и экран закрыт. */
  handleError(error: unknown): boolean {
    if (toProblem(error).status !== 403) {
      return false;
    }
    void this.recheck();
    return true;
  }

  async recheck(): Promise<void> {
    await this.auth.refreshProfile();
    if (!this.user.hasAnyRole(['ADMIN'])) {
      await this.router.navigateByUrl('/forbidden');
    }
  }
}
