import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { CursorPage } from './profile.service';

export type NotificationType =
  | 'FOLLOW' | 'MESSAGE' | 'CHAT_INVITATION' | 'COMMUNITY_INVITATION' | 'JOIN_REQUEST' | 'JOIN_RESULT'
  | 'COMMENT' | 'REPLY' | 'REACTION' | 'SYSTEM';

/** Куда вести: раздел и его идентификатор. Сервер отдаёт ссылку только к видимой цели. */
export interface NotificationLink {
  kind: 'CHAT' | 'POST' | 'GROUP' | 'USER';
  id: string | null;
  slug: string | null;
}

export interface NotificationItem {
  id: string;
  type: NotificationType;
  createdAt: string;
  read: boolean;
  actor: { id: string; username: string; displayName: string } | null;
  target: { kind: string; id: string } | null;
  link: NotificationLink | null;
}

/** Собственные уведомления: список, счётчик, прочтение. Чужие идентификаторы сервер отвечает 404. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);

  list(cursor: string | null): Promise<CursorPage<NotificationItem>> {
    let params = new HttpParams();
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return lastValueFrom(this.http.get<CursorPage<NotificationItem>>('/api/v1/me/notifications', { params }));
  }

  unreadCount(): Promise<{ unread: number }> {
    return lastValueFrom(this.http.get<{ unread: number }>('/api/v1/me/notifications/unread-count'));
  }

  markRead(id: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/me/notifications/${id}/read`, null)).then(() => undefined);
  }

  markAllRead(): Promise<{ updated: number }> {
    return lastValueFrom(this.http.post<{ updated: number }>('/api/v1/me/notifications/read-all', null));
  }
}
