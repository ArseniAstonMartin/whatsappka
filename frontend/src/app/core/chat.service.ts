import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { CursorPage } from './profile.service';

export interface LastMessage {
  seq: number;
  body: string | null;
  senderId: string;
  createdAt: string;
}

export type ConversationType = 'DIRECT' | 'GROUP';

/**
 * Диалог в списке — личный или групповой. unread — непрочитанные чужие неудалённые сообщения.
 * У DIRECT заполнены other*, у GROUP — title/avatarMediaId; блокировка — понятие только личного диалога.
 */
export interface Conversation {
  id: string;
  type: ConversationType;
  lastActivityAt: string;
  otherId: string | null;
  otherUsername: string | null;
  otherDisplayName: string | null;
  title: string | null;
  avatarMediaId: string | null;
  blocked: boolean;
  lastMessage: LastMessage | null;
  unread: number;
}

export interface Attachment {
  mediaId: string;
  position: number;
  purpose: string;
}

export interface ChatMessage {
  id: string;
  seq: number;
  senderId: string;
  body: string | null;
  createdAt: string;
  deleted: boolean;
  attachments: Attachment[];
}

/** Список, карточка, открытие личного диалога и история. Курсор истории — seq последнего показанного сообщения. */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  list(cursor: string | null): Promise<CursorPage<Conversation>> {
    return lastValueFrom(this.http.get<CursorPage<Conversation>>('/api/v1/conversations', { params: page(cursor) }));
  }

  get(conversationId: string): Promise<Conversation> {
    return lastValueFrom(this.http.get<Conversation>(`/api/v1/conversations/${conversationId}`));
  }

  /** Создаёт или возвращает уже существующий личный диалог с пользователем. */
  open(userId: string): Promise<{ id: string }> {
    return lastValueFrom(this.http.post<{ id: string }>('/api/v1/conversations', { userId }));
  }

  history(conversationId: string, cursor: string | null): Promise<CursorPage<ChatMessage>> {
    return lastValueFrom(
      this.http.get<CursorPage<ChatMessage>>(`/api/v1/conversations/${conversationId}/messages`, { params: page(cursor) }),
    );
  }
}

function page(cursor: string | null): HttpParams {
  let params = new HttpParams();
  if (cursor) {
    params = params.set('cursor', cursor);
  }
  return params;
}
