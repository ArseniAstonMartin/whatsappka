import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { CursorPage } from './profile.service';

export type CommunityVisibility = 'PUBLIC' | 'PRIVATE';
export type CommunityRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface CommunitySummary {
  id: string;
  slug: string;
  name: string;
  visibility: CommunityVisibility;
  avatarMediaId: string | null;
}

export interface CommunityView {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  visibility: CommunityVisibility;
  ownerId: string | null;
  avatarMediaId: string | null;
  coverMediaId: string | null;
  memberCount: number;
  viewerRole: CommunityRole | null;
  restricted: boolean;
}

export interface CommunityCreate {
  slug: string;
  name: string;
  description: string;
  visibility: CommunityVisibility;
}

export interface CommunityPatch {
  name?: string;
  description?: string;
  visibility?: CommunityVisibility;
}

export interface JoinRequestRef {
  id: string;
}

/** Запросы сообществ: каталог, карточка, создание/настройки и вступление/заявки (TASK-035..038). */
@Injectable({ providedIn: 'root' })
export class CommunityService {
  private readonly http = inject(HttpClient);

  catalog(cursor: string | null): Promise<CursorPage<CommunitySummary>> {
    return lastValueFrom(this.http.get<CursorPage<CommunitySummary>>('/api/v1/groups', { params: this.pageParams(cursor) }));
  }

  mine(): Promise<CommunitySummary[]> {
    return lastValueFrom(this.http.get<CommunitySummary[]>('/api/v1/me/groups'));
  }

  bySlug(slug: string): Promise<CommunityView> {
    return lastValueFrom(this.http.get<CommunityView>(`/api/v1/groups/slug/${encodeURIComponent(slug)}`));
  }

  create(body: CommunityCreate): Promise<{ id: string }> {
    return lastValueFrom(this.http.post<{ id: string }>('/api/v1/groups', body));
  }

  update(id: string, patch: CommunityPatch): Promise<void> {
    return lastValueFrom(this.http.patch(`/api/v1/groups/${id}`, patch)).then(() => undefined);
  }

  remove(id: string): Promise<void> {
    return lastValueFrom(this.http.delete(`/api/v1/groups/${id}`)).then(() => undefined);
  }

  attachAvatar(id: string, mediaId: string): Promise<void> {
    return lastValueFrom(this.http.put(`/api/v1/groups/${id}/avatar`, { mediaId })).then(() => undefined);
  }

  attachCover(id: string, mediaId: string): Promise<void> {
    return lastValueFrom(this.http.put(`/api/v1/groups/${id}/cover`, { mediaId })).then(() => undefined);
  }

  /** Прямое вступление — только для открытых сообществ, сервер отклонит иначе. */
  join(id: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/groups/${id}/join`, null)).then(() => undefined);
  }

  requestJoin(id: string): Promise<JoinRequestRef> {
    return lastValueFrom(this.http.post<JoinRequestRef>(`/api/v1/groups/${id}/join-requests`, null));
  }

  /** Своя ожидающая заявка в это сообщество, если есть. */
  myJoinRequest(id: string): Promise<JoinRequestRef | null> {
    return lastValueFrom(this.http.get<JoinRequestRef | null>(`/api/v1/groups/${id}/join-requests/mine`));
  }

  cancelJoinRequest(groupId: string, requestId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/groups/${groupId}/join-requests/${requestId}/cancel`, null)).then(() => undefined);
  }

  private pageParams(cursor: string | null): Record<string, string> {
    return cursor ? { cursor, limit: '20' } : { limit: '20' };
  }
}
