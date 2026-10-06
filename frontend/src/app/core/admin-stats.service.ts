import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

/** Агрегаты из базы. Даты — UTC, конец периода включается целиком. */
export interface AdminStats {
  from: string;
  to: string;
  users: number;
  dailyActiveUsers: number;
  postsPublished: number;
  messagesSent: number;
  groups: number;
  openReports: number;
  mediaBytes: number;
}

@Injectable({ providedIn: 'root' })
export class AdminStatsService {
  private readonly http = inject(HttpClient);

  get(from: string, to: string): Promise<AdminStats> {
    const params = new HttpParams().set('from', from).set('to', to);
    return lastValueFrom(this.http.get<AdminStats>('/api/v1/admin/stats', { params }));
  }
}
