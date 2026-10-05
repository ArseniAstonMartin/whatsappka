import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CurrentUser } from './current-user';

/** Скрывает служебный раздел в интерфейсе. Сервер всё равно отвечает 403 на запросы без роли. */
export const requireRoles = (...roles: string[]): CanActivateFn => () => {
  const user = inject(CurrentUser);
  return user.hasAnyRole(roles) || inject(Router).createUrlTree(['/forbidden']);
};
