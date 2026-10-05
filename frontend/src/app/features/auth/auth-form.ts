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
