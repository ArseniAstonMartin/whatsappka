import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { CursorPage } from './profile.service';
import { ReactionType } from './reaction.service';

export type PostStatus = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED';

export interface PostView {
  id: string;
  authorId: string;
  authorUsername: string;
  authorDisplayName: string;
  authorAvatarMediaId: string | null;
  body: string | null;
  status: PostStatus;
  groupId: string | null;
  mediaIds: string[];
  hashtags: string[];
  version: number;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  scheduleFailureReason: string | null;
  reactionCounts: Partial<Record<ReactionType, number>>;
  viewerReaction: ReactionType | null;
}

export interface PostSummary {
  id: string;
  body: string | null;
  status: PostStatus;
  groupId: string | null;
  version: number;
  updatedAt: string;
  scheduleFailureReason: string | null;
}

export interface PostSummaryPublic {
  id: string;
  body: string | null;
  authorId: string;
  authorUsername: string;
  authorDisplayName: string;
  authorAvatarMediaId: string | null;
  groupId: string | null;
  mediaIds: string[];
  hashtags: string[];
  publishedAt: string;
  updatedAt: string;
}

export interface PostRef {
  id: string;
}

/** Черновики, публикация и расписание постов (TASK-041..046). */
@Injectable({ providedIn: 'root' })
export class PostService {
  private readonly http = inject(HttpClient);

  create(groupId: string | null, body: string | null, media: string[], hashtags: string[]): Promise<PostRef> {
    return lastValueFrom(this.http.post<PostRef>('/api/v1/posts', { groupId, body, media, hashtags }));
  }

  get(id: string): Promise<PostView> {
    return lastValueFrom(this.http.get<PostView>(`/api/v1/posts/${id}`));
  }

  update(id: string, version: number, body: string | null, media: string[], hashtags: string[]): Promise<void> {
    return lastValueFrom(this.http.patch(`/api/v1/posts/${id}`, { body, media, hashtags, version })).then(() => undefined);
  }

  remove(id: string): Promise<void> {
    return lastValueFrom(this.http.delete(`/api/v1/posts/${id}`)).then(() => undefined);
  }

  /** Idempotency-Key защищает от второй публикации при повторе запроса после разрыва связи. */
  publish(id: string): Promise<PostRef> {
    return lastValueFrom(
      this.http.post<PostRef>(`/api/v1/posts/${id}/publish`, null, { headers: { 'Idempotency-Key': crypto.randomUUID() } }),
    );
  }

  /** publishAt — ISO-момент в UTC; выбор даты/времени и часовой пояс считает экран редактора. */
  schedule(id: string, publishAt: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/posts/${id}/schedule`, { publishAt })).then(() => undefined);
  }

  cancelSchedule(id: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/posts/${id}/schedule/cancel`, null)).then(() => undefined);
  }

  drafts(cursor: string | null): Promise<CursorPage<PostSummary>> {
    return lastValueFrom(
      this.http.get<CursorPage<PostSummary>>('/api/v1/me/post-drafts', { params: this.pageParams(cursor) }),
    );
  }

  feed(cursor: string | null): Promise<CursorPage<PostSummaryPublic>> {
    return lastValueFrom(this.http.get<CursorPage<PostSummaryPublic>>('/api/v1/feed', { params: this.pageParams(cursor) }));
  }

  profilePosts(authorId: string, cursor: string | null): Promise<CursorPage<PostSummaryPublic>> {
    return lastValueFrom(
      this.http.get<CursorPage<PostSummaryPublic>>(`/api/v1/users/${authorId}/posts`, { params: this.pageParams(cursor) }),
    );
  }

  groupPosts(groupId: string, cursor: string | null): Promise<CursorPage<PostSummaryPublic>> {
    return lastValueFrom(
      this.http.get<CursorPage<PostSummaryPublic>>(`/api/v1/groups/${groupId}/posts`, { params: this.pageParams(cursor) }),
    );
  }

  private pageParams(cursor: string | null): Record<string, string> {
    return cursor ? { cursor, limit: '20' } : { limit: '20' };
  }
}
