import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { CursorPage } from './profile.service';

export type StaffRole = 'MODERATOR' | 'ADMIN';

export interface AdminUser {
  id: string;
  username: string;
  displayName: string;
  status: string;
  verified: boolean;
  roles: string[];
}

/** Управление пользователями. Все методы — только для администратора; сервер проверяет это повторно. */
@Injectable({ providedIn: 'root' })
export class AdminUsersService {
  private readonly http = inject(HttpClient);

  list(query: string, cursor: string | null): Promise<CursorPage<AdminUser>> {
    let params = new HttpParams();
    if (query) {
      params = params.set('q', query);
    }
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return lastValueFrom(this.http.get<CursorPage<AdminUser>>('/api/v1/admin/users', { params }));
  }

  setRoles(userId: string, roles: StaffRole[], reason: string): Promise<{ userId: string; roles: string[] }> {
    return lastValueFrom(this.http.put<{ userId: string; roles: string[] }>(`/api/v1/admin/users/${userId}/roles`, { roles, reason }));
  }

  setVerified(userId: string, verified: boolean, reason: string): Promise<{ userId: string; verified: boolean }> {
    return lastValueFrom(this.http.put<{ userId: string; verified: boolean }>(`/api/v1/admin/users/${userId}/verified`, { verified, reason }));
  }
}
