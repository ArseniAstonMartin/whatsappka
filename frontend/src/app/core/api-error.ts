import { HttpErrorResponse } from '@angular/common/http';

export interface ApiProblem {
  status: number;
  code: string;
  message: string;
  fields: Record<string, string>;
}

interface ProblemBody {
  code?: string;
  detail?: string;
  fieldErrors?: { field: string; message: string }[];
}

/** Русский текст по коду ошибки. Текст сервера используется только там, где кода нет. */
const MESSAGES: Record<string, string> = {
  invalid_credentials: 'Неверный email или пароль',
  account_disabled: 'Аккаунт отключён. Обратитесь в поддержку.',
  conflict: 'Не удалось зарегистрировать аккаунт с такими данными',
  validation_failed: 'Проверьте поля формы',
  too_many_requests: 'Слишком много попыток. Подождите и попробуйте снова.',
  unauthorized: 'Сессия истекла. Войдите снова.',
  invalid_refresh_token: 'Сессия истекла. Войдите снова.',
};

export function toProblem(error: unknown): ApiProblem {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: 0, code: 'unknown', message: 'Что-то пошло не так. Попробуйте позже.', fields: {} };
  }
  if (error.status === 0) {
    return { status: 0, code: 'network', message: 'Нет связи с сервером. Проверьте подключение.', fields: {} };
  }
  const body = (error.error ?? {}) as ProblemBody;
  const code = body.code ?? 'request_failed';
  const fields: Record<string, string> = {};
  for (const item of body.fieldErrors ?? []) {
    fields[item.field] = item.message;
  }
  return {
    status: error.status,
    code,
    message: MESSAGES[code] ?? 'Не удалось выполнить действие. Попробуйте позже.',
    fields,
  };
}
