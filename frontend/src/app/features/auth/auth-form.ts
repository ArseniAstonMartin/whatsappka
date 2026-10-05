import { AbstractControl, FormGroup } from '@angular/forms';
import { ApiProblem } from '../../core/api-error';

/** Переносит ошибки полей сервера на контролы формы; текст снимается при следующем изменении. */
export function applyServerErrors(form: FormGroup, problem: ApiProblem): void {
  for (const [field, message] of Object.entries(problem.fields)) {
    const control: AbstractControl | null = form.get(field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: message });
      control.markAsTouched();
    }
  }
}

/** Безопасный адрес возврата: только внутренний путь, без внешних адресов. */
export function safeNext(raw: string | null): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//')) {
    return '/feed';
  }
  return raw;
}

/** Текст ошибки входа через Google из адреса возврата. Коды Google не показываются как есть. */
export function googleErrorText(code: string | null): string | null {
  if (!code) {
    return null;
  }
  return googleErrors[code] ?? 'Вход через Google не выполнен. Попробуйте ещё раз или войдите по почте и паролю.';
}

const googleErrors: Record<string, string> = {
  google_not_configured: 'Вход через Google сейчас не настроен на сервере. Войдите по почте и паролю.',
  google_unavailable: 'Google сейчас недоступен. Попробуйте позже или войдите по почте и паролю.',
  google_cancelled: 'Вход через Google отменён.',
  google_email_unverified: 'Google не подтвердил адрес этой почты.',
  email_in_use: 'Аккаунт с этим email уже есть. Войдите по почте и паролю и привяжите Google в настройках безопасности.',
};
