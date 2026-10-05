import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Повтор уже сделан: второго refresh и второго повтора не будет (защита от цикла refresh/401). */
const RETRIED = new HttpContextToken<boolean>(() => false);

/** Эти операции сами управляют входом и не получают Bearer и не запускают refresh. */
const SESSION_CALLS = /^\/api\/v1\/auth\/(login|register|refresh|logout)$/;

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (SESSION_CALLS.test(request.url)) {
    return next(request);
  }
  const auth = inject(AuthService);
  const first = withToken(request, auth.token());
  return next(first).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || request.context.get(RETRIED)) {
        return throwError(() => error);
      }
      return from(auth.refresh()).pipe(
        switchMap((renewed) => {
          if (!renewed) {
            return throwError(() => error);
          }
          const retry = withToken(request.clone({ context: request.context.set(RETRIED, true) }), auth.token());
          return next(retry);
        }),
      );
    }),
  );
};

function withToken(request: Parameters<HttpInterceptorFn>[0], token: string | null) {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}
