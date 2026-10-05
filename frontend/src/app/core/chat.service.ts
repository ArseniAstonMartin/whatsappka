import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { lastValueFrom, timeout } from 'rxjs';
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
  updatedAt: string;
  version: number;
  deleted: boolean;
  attachments: Attachment[];
  clientMessageId: string | null;
}

/** Метаданные журнала; актуальные снимки сообщений передаются вместе со страницей событий. */
export interface ChatEvent {
  eventSeq: number;
  type: string;
  occurredAt: string;
  actorId: string | null;
  messageId: string | null;
  messageSeq: number | null;
}

export interface ChatEventsPage {
  items: ChatEvent[];
  nextCursor: number | null;
  hasMore: boolean;
  fullSyncRequired: boolean;
  membershipId: string;
  messages: ChatMessage[];
}

export interface ChatEventsHead {
  cursor: number;
  membershipId: string;
}

/** Сколько участников, имевших доступ к сообщению, уже прочитали его. */
export interface ReadStatus {
  readBy: number;
  eligible: number;
}

export interface EditedMessage {
  id: string;
  version: number;
  updatedAt: string;
}

/** Список, карточка, открытие личного диалога и история. Курсор истории — seq последнего показанного сообщения. */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  list(cursor: string | null): Promise<CursorPage<Conversation>> {
    return lastValueFrom(this.http.get<CursorPage<Conversation>>('/api/v1/conversations', { params: page(cursor) }));
  }

  get(conversationId: string): Promise<Conversation> {
    return lastValueFrom(this.http.get<Conversation>(`/api/v1/conversations/${conversationId}`).pipe(timeout(10000)));
  }

  /** Создаёт или возвращает уже существующий личный диалог с пользователем. */
  open(userId: string): Promise<{ id: string }> {
    return lastValueFrom(this.http.post<{ id: string }>('/api/v1/conversations', { userId }));
  }

  history(conversationId: string, cursor: string | null): Promise<CursorPage<ChatMessage>> {
    return lastValueFrom(
      this.http.get<CursorPage<ChatMessage>>(`/api/v1/conversations/${conversationId}/messages`, { params: page(cursor) }).pipe(timeout(10000)),
    );
  }

  /** Граница журнала событий. Берётся до истории, чтобы ни одно событие не выпало между ними. */
  eventsHead(conversationId: string): Promise<ChatEventsHead> {
    return lastValueFrom(this.http.get<ChatEventsHead>(`/api/v1/conversations/${conversationId}/events/head`).pipe(timeout(10000)));
  }

  events(conversationId: string, cursor: number): Promise<ChatEventsPage> {
    const params = new HttpParams().set('cursor', String(cursor));
    return lastValueFrom(this.http.get<ChatEventsPage>(`/api/v1/conversations/${conversationId}/events`, { params }).pipe(timeout(10000)));
  }

  readStatus(conversationId: string, messageId: string): Promise<ReadStatus> {
    return lastValueFrom(
      this.http.get<ReadStatus>(`/api/v1/conversations/${conversationId}/messages/${messageId}/read-status`),
    );
  }

  /** Правка доступна автору в течение суток после отправки; seq не меняется. */
  editMessage(conversationId: string, messageId: string, body: string | null): Promise<EditedMessage> {
    return lastValueFrom(
      this.http.patch<EditedMessage>(`/api/v1/conversations/${conversationId}/messages/${messageId}`, { body }),
    );
  }

  /** Автор удаляет своё сообщение без причины; модератор чата — чужое только с причиной. */
  removeMessage(conversationId: string, messageId: string, reason: string | null): Promise<void> {
    return lastValueFrom(
      this.http.delete(`/api/v1/conversations/${conversationId}/messages/${messageId}`, { body: reason ? { reason } : undefined }),
    ).then(() => undefined);
  }
}

function page(cursor: string | null): HttpParams {
  let params = new HttpParams();
  if (cursor) {
    params = params.set('cursor', cursor);
  }
  return params;
}
