import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Гостя направляет на вход и запоминает, куда он шёл. Сервер всё равно проверяет каждый запрос. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.status() === 'authenticated') {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { next: state.url } });
};
