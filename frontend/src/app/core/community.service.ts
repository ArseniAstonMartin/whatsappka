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

export interface CommunityMember {
  id: string;
  username: string;
  displayName: string;
  role: CommunityRole;
}

export interface CommunityJoinRequestItem {
  id: string;
  requesterId: string;
  username: string;
  displayName: string;
  createdAt: string;
}

export interface GroupInvitation {
  id: string;
  groupId: string;
  groupName: string;
  expiresAt: string;
}

/** Запросы сообществ: каталог, карточка, создание/настройки, вступление/заявки и состав (TASK-035..038, 040). */
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

  /** Состав виден без ограничений для открытого сообщества, иначе — только участникам (проверяет сервер). */
  members(groupId: string): Promise<CommunityMember[]> {
    return lastValueFrom(this.http.get<CommunityMember[]>(`/api/v1/groups/${groupId}/members`));
  }

  leave(groupId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/groups/${groupId}/leave`, null)).then(() => undefined);
  }

  removeMember(groupId: string, userId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/groups/${groupId}/members/${userId}/remove`, null)).then(() => undefined);
  }

  setMemberRole(groupId: string, userId: string, role: 'ADMIN' | 'MEMBER'): Promise<void> {
    return lastValueFrom(this.http.put(`/api/v1/groups/${groupId}/members/${userId}/role`, { role })).then(() => undefined);
  }

  transferOwnership(groupId: string, userId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/groups/${groupId}/transfer`, { userId })).then(() => undefined);
  }

  /** Заявки на вступление: видит только OWNER/ADMIN сообщества. */
  pendingJoinRequests(groupId: string): Promise<CommunityJoinRequestItem[]> {
    return lastValueFrom(this.http.get<CommunityJoinRequestItem[]>(`/api/v1/groups/${groupId}/join-requests`));
  }

  acceptJoinRequest(groupId: string, requestId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/groups/${groupId}/join-requests/${requestId}/accept`, null)).then(() => undefined);
  }

  rejectJoinRequest(groupId: string, requestId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/groups/${groupId}/join-requests/${requestId}/reject`, null)).then(() => undefined);
  }

  /** Создание защищено Idempotency-Key: повтор после разрыва связи не создаёт второе приглашение. */
  invite(groupId: string, userId: string): Promise<JoinRequestRef> {
    return lastValueFrom(
      this.http.post<JoinRequestRef>(
        `/api/v1/groups/${groupId}/invitations`,
        { userId },
        { headers: { 'Idempotency-Key': crypto.randomUUID() } },
      ),
    );
  }

  myInvitations(): Promise<GroupInvitation[]> {
    return lastValueFrom(this.http.get<GroupInvitation[]>('/api/v1/me/group-invitations'));
  }

  acceptInvitation(invitationId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/group-invitations/${invitationId}/accept`, null)).then(() => undefined);
  }

  declineInvitation(invitationId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/group-invitations/${invitationId}/decline`, null)).then(() => undefined);
  }

  private pageParams(cursor: string | null): Record<string, string> {
    return cursor ? { cursor, limit: '20' } : { limit: '20' };
  }
}
