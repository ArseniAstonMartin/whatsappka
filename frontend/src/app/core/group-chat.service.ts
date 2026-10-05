import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export type GroupChatRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface GroupChatMember {
  id: string;
  username: string;
  displayName: string;
  role: GroupChatRole;
}

export interface GroupChatInvitation {
  id: string;
  conversationId: string;
  conversationTitle: string;
  expiresAt: string;
}

/** Создание и управление групповым чатом: состав, роли, передача владения, приглашения (TASK-062). */
@Injectable({ providedIn: 'root' })
export class GroupChatService {
  private readonly http = inject(HttpClient);

  create(title: string): Promise<{ id: string }> {
    return lastValueFrom(
      this.http.post<{ id: string }>('/api/v1/conversations/groups', { title, avatarMediaId: null }),
    );
  }

  rename(conversationId: string, title: string): Promise<void> {
    return lastValueFrom(this.http.patch(`/api/v1/conversations/${conversationId}`, { title })).then(() => undefined);
  }

  changeAvatar(conversationId: string, mediaId: string): Promise<void> {
    return lastValueFrom(this.http.put(`/api/v1/conversations/${conversationId}/avatar`, { mediaId })).then(() => undefined);
  }

  members(conversationId: string): Promise<GroupChatMember[]> {
    return lastValueFrom(this.http.get<GroupChatMember[]>(`/api/v1/conversations/${conversationId}/members`));
  }

  leave(conversationId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/conversations/${conversationId}/leave`, null)).then(() => undefined);
  }

  removeMember(conversationId: string, userId: string): Promise<void> {
    return lastValueFrom(
      this.http.post(`/api/v1/conversations/${conversationId}/members/${userId}/remove`, null),
    ).then(() => undefined);
  }

  setMemberRole(conversationId: string, userId: string, role: 'ADMIN' | 'MEMBER'): Promise<void> {
    return lastValueFrom(
      this.http.put(`/api/v1/conversations/${conversationId}/members/${userId}/role`, { role }),
    ).then(() => undefined);
  }

  transferOwnership(conversationId: string, userId: string): Promise<void> {
    return lastValueFrom(
      this.http.post(`/api/v1/conversations/${conversationId}/transfer`, { userId }),
    ).then(() => undefined);
  }

  /** Создание требует Idempotency-Key: повтор после разрыва связи не отправляет второе приглашение. */
  invite(conversationId: string, userId: string): Promise<{ id: string }> {
    return lastValueFrom(
      this.http.post<{ id: string }>(
        `/api/v1/conversations/${conversationId}/invitations`,
        { userId },
        { headers: { 'Idempotency-Key': crypto.randomUUID() } },
      ),
    );
  }

  myInvitations(): Promise<GroupChatInvitation[]> {
    return lastValueFrom(this.http.get<GroupChatInvitation[]>('/api/v1/me/invitations'));
  }

  acceptInvitation(invitationId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/invitations/${invitationId}/accept`, null)).then(() => undefined);
  }

  declineInvitation(invitationId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/invitations/${invitationId}/decline`, null)).then(() => undefined);
  }
}
