import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { NotificationType } from './notification.service';

/** Настройка одной категории. canDisable=false — категория обязательна: сервер её не выключает. */
export interface NotificationPreference {
  type: NotificationType;
  enabled: boolean;
  canDisable: boolean;
}

/** Категории уведомлений пользователя. Сервер хранит только отключённые; по умолчанию все включены. */
@Injectable({ providedIn: 'root' })
export class NotificationPreferenceService {
  private readonly http = inject(HttpClient);

  list(): Promise<NotificationPreference[]> {
    return lastValueFrom(this.http.get<NotificationPreference[]>('/api/v1/me/notification-preferences'));
  }

  set(type: NotificationType, enabled: boolean): Promise<NotificationPreference> {
    return lastValueFrom(
      this.http.put<NotificationPreference>(`/api/v1/me/notification-preferences/${type}`, { enabled }),
    );
  }
}
