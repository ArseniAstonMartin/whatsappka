import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export interface SessionItem {
  id: string;
  deviceLabel: string;
  createdAt: string;
  lastUsedAt: string;
  current: boolean;
}

interface SessionPage {
  items: SessionItem[];
}

/** Сеансы и привязка Google для вошедшего пользователя. Токены сюда не попадают: только идентификаторы и подписи устройств. */
@Injectable({ providedIn: 'root' })
export class AccountSecurityService {
  private readonly http = inject(HttpClient);

  async sessions(): Promise<SessionItem[]> {
    const page = await lastValueFrom(this.http.get<SessionPage>('/api/v1/auth/sessions'));
    return page.items;
  }

  revokeSession(id: string): Promise<void> {
    return lastValueFrom(this.http.delete<void>(`/api/v1/auth/sessions/${id}`));
  }

  logoutAll(): Promise<void> {
    return lastValueFrom(this.http.post<void>('/api/v1/auth/logout-all', {}));
  }

  /** Адрес для перехода к Google. Привязка начинается только по явному действию пользователя. */
  async startGoogleLink(): Promise<string> {
    const response = await lastValueFrom(
      this.http.post<{ authorizationUrl: string }>('/api/v1/auth/google/link', {}),
    );
    return response.authorizationUrl;
  }
}
