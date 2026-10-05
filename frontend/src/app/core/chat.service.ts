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

/** Личный диалог в списке. unread — непрочитанные чужие неудалённые сообщения. */
export interface Conversation {
  id: string;
  type: string;
  lastActivityAt: string;
  otherId: string;
  otherUsername: string;
  otherDisplayName: string;
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
