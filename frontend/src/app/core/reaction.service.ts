import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export type ReactionType = 'LIKE' | 'HEART' | 'LAUGH' | 'WOW' | 'SAD' | 'SUPPORT';

/** Реакции на посты и комментарии (TASK-050). PUT идемпотентно задаёт тип, DELETE снимает. */
@Injectable({ providedIn: 'root' })
export class ReactionService {
  private readonly http = inject(HttpClient);

  setPostReaction(postId: string, type: ReactionType): Promise<void> {
    return lastValueFrom(this.http.put(`/api/v1/posts/${postId}/reaction`, { type })).then(() => undefined);
  }

  removePostReaction(postId: string): Promise<void> {
    return lastValueFrom(this.http.delete(`/api/v1/posts/${postId}/reaction`)).then(() => undefined);
  }

  setCommentReaction(commentId: string, type: ReactionType): Promise<void> {
    return lastValueFrom(this.http.put(`/api/v1/comments/${commentId}/reaction`, { type })).then(() => undefined);
  }

  removeCommentReaction(commentId: string): Promise<void> {
    return lastValueFrom(this.http.delete(`/api/v1/comments/${commentId}/reaction`)).then(() => undefined);
  }
}
