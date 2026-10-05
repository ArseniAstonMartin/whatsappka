import { Injectable, signal } from '@angular/core';

/**
 * Роли текущего пользователя для скрытия разделов. Это только интерфейс: доступ проверяет сервер.
 * Значения выставляет слой авторизации; до входа список пуст, то есть гость.
 */
@Injectable({ providedIn: 'root' })
export class CurrentUser {
  private readonly rolesState = signal<readonly string[]>([]);

  readonly roles = this.rolesState.asReadonly();

  setRoles(roles: readonly string[]): void {
    this.rolesState.set([...roles]);
  }

  hasAnyRole(required: readonly string[]): boolean {
    const current = this.rolesState();
    return required.some((role) => current.includes(role));
  }
}
