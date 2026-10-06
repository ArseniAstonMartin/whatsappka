import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { CursorPage } from './profile.service';

/** Запись журнала аудита без служебных идентификаторов запроса. */
export interface AuditEntry {
  id: string;
  actor: string;
  action: string;
  targetType: string;
  targetId: string | null;
  reason: string | null;
  occurredAt: string;
}

export interface AuditFilter {
  action: string | null;
  targetType: string | null;
  cursor: string | null;
}

/** Только чтение. Сервер сам ограничивает модератора его собственными разрешёнными решениями. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  list(filter: AuditFilter): Promise<CursorPage<AuditEntry>> {
    let params = new HttpParams();
    if (filter.action) params = params.set('action', filter.action);
    if (filter.targetType) params = params.set('targetType', filter.targetType);
    if (filter.cursor) params = params.set('cursor', filter.cursor);
    return lastValueFrom(this.http.get<CursorPage<AuditEntry>>('/api/v1/moderation/audit', { params }));
  }
}
