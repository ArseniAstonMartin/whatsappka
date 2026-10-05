import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { CursorPage } from './profile.service';

export type ModerationStatus = 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'REJECTED';
export type ModerationTargetKind = 'USER' | 'POST' | 'COMMENT' | 'MESSAGE' | 'GROUP';
export type ModerationDecision = 'HIDE' | 'NO_ACTION';

export interface QueueItem {
  id: string;
  targetKind: ModerationTargetKind;
  reason: string;
  status: ModerationStatus;
  createdAt: string;
  assigneeId: string | null;
  decidedAt: string | null;
}

/** Минимальное доказательство: снимок содержит только то, что нужно для решения. */
export interface Evidence {
  reportId: string;
  targetKind: ModerationTargetKind;
  reason: string;
  status: ModerationStatus;
  createdAt: string;
  snapshot: Record<string, unknown> | null;
}

export interface JournalEntry {
  id: string;
  reportId: string;
  action: string;
  fromStatus: string | null;
  toStatus: string;
  reason: string | null;
  actorUsername: string;
  createdAt: string;
}

export const REASON_LABELS: Record<string, string> = {
  SPAM: 'Спам или реклама', HARASSMENT: 'Травля или оскорбления', HATE: 'Ненависть по признаку',
  VIOLENCE: 'Насилие', SEXUAL: 'Сексуальный контент', SELF_HARM: 'Самоповреждение',
  IMPERSONATION: 'Выдаёт себя за другого', OTHER: 'Другое',
};

export const KIND_LABELS: Record<ModerationTargetKind, string> = {
  USER: 'Пользователь', POST: 'Публикация', COMMENT: 'Комментарий', MESSAGE: 'Сообщение', GROUP: 'Сообщество',
};

export const ACTION_LABELS: Record<string, string> = {
  TAKE: 'Взял(а) в работу',
  REJECT: 'Отклонил(а) жалобу',
  RESOLVE_HIDE: 'Скрыл(а) материал',
  RESOLVE_NO_ACTION: 'Решил(а): нарушения нет',
  RESTORE: 'Восстановил(а) материал',
};

/** Рабочие действия модератора. Каждое требует причину; сервер проверяет роль и назначение. */
@Injectable({ providedIn: 'root' })
export class ModerationService {
  private readonly http = inject(HttpClient);

  queue(status: ModerationStatus, cursor: string | null): Promise<CursorPage<QueueItem>> {
    let params = new HttpParams().set('status', status);
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return lastValueFrom(this.http.get<CursorPage<QueueItem>>('/api/v1/moderation/reports', { params }));
  }

  evidence(reportId: string): Promise<Evidence> {
    return lastValueFrom(this.http.get<Evidence>(`/api/v1/moderation/reports/${reportId}/evidence`));
  }

  take(reportId: string): Promise<unknown> {
    return lastValueFrom(this.http.post(`/api/v1/moderation/reports/${reportId}/take`, null));
  }

  resolve(reportId: string, action: ModerationDecision, reason: string): Promise<unknown> {
    return lastValueFrom(this.http.post(`/api/v1/moderation/reports/${reportId}/resolve`, { action, reason }));
  }

  reject(reportId: string, reason: string): Promise<unknown> {
    return lastValueFrom(this.http.post(`/api/v1/moderation/reports/${reportId}/reject`, { reason }));
  }

  restore(reportId: string, reason: string): Promise<unknown> {
    return lastValueFrom(this.http.post(`/api/v1/admin/moderation/reports/${reportId}/restore`, { reason }));
  }

  /** Временная блокировка модератором или администратором. */
  temporarySanction(userId: string, durationHours: number, reason: string): Promise<unknown> {
    return lastValueFrom(this.http.post(`/api/v1/moderation/users/${userId}/sanctions`, { durationHours, reason }));
  }

  /** Постоянная блокировка — только администратор. */
  permanentSanction(userId: string, reason: string): Promise<unknown> {
    return lastValueFrom(this.http.post(`/api/v1/admin/users/${userId}/sanctions`, { kind: 'PERMANENT', reason }));
  }

  journal(cursor: string | null): Promise<CursorPage<JournalEntry>> {
    let params = new HttpParams();
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return lastValueFrom(this.http.get<CursorPage<JournalEntry>>('/api/v1/moderation/actions', { params }));
  }
}

/** Владелец содержимого из снимка: кого можно ограничить. У сообщения — отправитель. */
export function authorIdOf(evidence: Evidence): string | null {
  const s = evidence.snapshot;
  if (!s) {
    return null;
  }
  const value = s['authorId'] ?? s['senderId'] ?? s['userId'];
  return typeof value === 'string' ? value : null;
}
