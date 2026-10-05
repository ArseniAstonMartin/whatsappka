import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { CursorPage } from './profile.service';
import { ReactionType } from './reaction.service';

export interface CommentView {
  id: string;
  postId: string;
  parentId: string | null;
  replyToUserId: string | null;
  authorId: string;
  authorUsername: string;
  authorDisplayName: string;
  authorAvatarMediaId: string | null;
  body: string | null;
  deleted: boolean;
  edited: boolean;
  depth: number;
  createdAt: string;
  updatedAt: string;
  version: number;
  reactionCounts: Partial<Record<ReactionType, number>>;
  viewerReaction: ReactionType | null;
}

export interface CommentRef {
  id: string;
}

/** Вложенные комментарии к публикации (TASK-048). */
@Injectable({ providedIn: 'root' })
export class CommentService {
  private readonly http = inject(HttpClient);

  list(postId: string, cursor: string | null): Promise<CursorPage<CommentView>> {
    return lastValueFrom(
      this.http.get<CursorPage<CommentView>>(`/api/v1/posts/${postId}/comments`, {
        params: cursor ? { cursor, limit: '20' } : { limit: '20' },
      }),
    );
  }

  create(postId: string, body: string, parentId: string | null): Promise<CommentRef> {
    return lastValueFrom(this.http.post<CommentRef>(`/api/v1/posts/${postId}/comments`, { body, parentId }));
  }

  update(id: string, body: string, version: number): Promise<void> {
    return lastValueFrom(this.http.patch(`/api/v1/comments/${id}`, { body, version })).then(() => undefined);
  }

  remove(id: string): Promise<void> {
    return lastValueFrom(this.http.delete(`/api/v1/comments/${id}`)).then(() => undefined);
  }
}
