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
  file_too_large: 'Файл больше допустимого размера для этого назначения',
  unsupported_media_type: 'Этот формат файла не поддерживается',
  mime_mismatch: 'Тип файла не совпадает с его содержимым',
  quota_exceeded: 'Превышена квота хранилища. Удалите ненужные файлы.',
  storage_limit_reached: 'Общий лимит хранилища исчерпан. Попробуйте позже.',
  disk_space_low: 'Сейчас недостаточно места для загрузок. Попробуйте позже.',
  storage_unavailable: 'Хранилище файлов временно недоступно. Попробуйте позже.',
  length_required: 'Не удалось определить размер файла',
  media_not_ready: 'Файл ещё обрабатывается',
  media_in_use: 'Файл привязан и не может быть удалён',
  purpose_mismatch: 'Файл не подходит для этого места',
  invalid_purpose: 'Неизвестное назначение файла',
  invalid_cursor: 'Не удалось открыть эту часть списка. Обновите страницу.',
  self_chat: 'Нельзя написать самому себе.',
  interaction_blocked: 'Отправка недоступна: между вами блокировка.',
  already_member: 'Вы уже состоите в сообществе',
  private_group: 'Сообщество приватно, нужна заявка на вступление',
  public_group: 'Открытое сообщество не требует заявки',
  request_pending: 'Заявка уже ожидает решения',
  join_request_cooldown: 'Повторная заявка возможна не раньше чем через 24 часа после отказа',
  request_closed: 'Заявка уже закрыта',
  not_draft: 'Публиковать можно только черновик',
  version_conflict: 'Запись изменена в другом месте',
  edit_not_allowed: 'Запись сейчас недоступна для редактирования',
  empty_post: 'Нужен текст или хотя бы одно готовое изображение',
  attachment_in_use: 'Изображение уже используется в другой публикации',
  not_scheduled: 'Запись не отложена',
  schedule_not_allowed: 'Запись опубликована — расписание недоступно',
  schedule_in_progress: 'Публикация уже выполняется, повторите через минуту',
  bad_request: 'Проверьте запрос и попробуйте снова',
  comment_deleted: 'Комментарий удалён',
  already_deleted: 'Комментарий уже удалён',
  invalid_reaction_type: 'Неизвестный тип реакции',
  not_found: 'Объект не найден',
};

/** Тот же словарь кодов, которым пользуется {@link toProblem} — для ошибок не из HTTP (например, STOMP). */
export function messageForCode(code: string): string {
  return MESSAGES[code] ?? 'Не удалось выполнить действие. Попробуйте позже.';
}

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
    message: messageForCode(code),
    fields,
  };
}
