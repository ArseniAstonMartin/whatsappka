import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { toProblem } from './api-error';

export type ReportTargetKind = 'USER' | 'POST' | 'COMMENT' | 'MESSAGE' | 'GROUP';

export type ReportReason =
  | 'SPAM' | 'HARASSMENT' | 'HATE' | 'VIOLENCE' | 'SEXUAL' | 'SELF_HARM' | 'IMPERSONATION' | 'OTHER';

export const REPORT_REASONS: readonly { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: 'Спам или реклама' },
  { value: 'HARASSMENT', label: 'Травля или оскорбления' },
  { value: 'HATE', label: 'Ненависть по признаку' },
  { value: 'VIOLENCE', label: 'Насилие' },
  { value: 'SEXUAL', label: 'Сексуальный контент' },
  { value: 'SELF_HARM', label: 'Самоповреждение' },
  { value: 'IMPERSONATION', label: 'Выдаёт себя за другого' },
  { value: 'OTHER', label: 'Другое' },
];

/** Жалоба и уведомление автора о скрытии. Клиент шлёт только вид цели, её id, причину и описание — не содержимое. */
@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);

  submit(targetKind: ReportTargetKind, targetId: string, reason: ReportReason, description: string | null): Promise<{ id: string; status: string }> {
    return lastValueFrom(this.http.post<{ id: string; status: string }>('/api/v1/reports', { targetKind, targetId, reason, description }));
  }

  /** Причина скрытия публикации — только для её автора; для остальных сервер отвечает 404. */
  hiddenPostReason(postId: string): Promise<{ postId: string; reason: string | null }> {
    return lastValueFrom(this.http.get<{ postId: string; reason: string | null }>(`/api/v1/posts/${postId}/moderation-notice`));
  }
}

/** Текст результата без технических деталей: код ошибки переводится, идентификаторы не показываются. */
export function reportFailureText(error: unknown): string {
  const problem = toProblem(error);
  switch (problem.code) {
    case 'report_already_open':
      return 'На этот материал уже есть ваша открытая жалоба. Повторно пожаловаться можно после её рассмотрения.';
    case 'self_report':
      return 'Нельзя пожаловаться на себя.';
  }
  if (problem.status === 404) {
    return 'Этот материал сейчас недоступен, поэтому пожаловаться на него нельзя.';
  }
  return problem.message;
}
