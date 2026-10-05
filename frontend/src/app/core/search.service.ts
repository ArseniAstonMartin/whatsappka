import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { CursorPage } from './profile.service';
import { PostSummaryPublic } from './post.service';

export interface UserHit {
  id: string;
  username: string;
  displayName: string;
}

export interface PostHit {
  id: string;
  authorId: string;
  authorUsername: string;
  authorDisplayName: string;
  publishedAt: string;
  snippet: string | null;
}

/** У приватного сообщества restricted = true: описания и аватара в результате нет. */
export interface GroupHit {
  id: string;
  slug: string;
  name: string;
  visibility: string;
  restricted: boolean;
  avatarMediaId: string | null;
}

export interface HashtagHit {
  name: string;
  displayName: string;
  popularity: number;
}

/** Поиск по четырём видам и лента хештега. Сервер проверяет запрос, видимость и предел страницы. */
@Injectable({ providedIn: 'root' })
export class SearchService {
  private readonly http = inject(HttpClient);

  users(q: string, cursor: string | null): Promise<CursorPage<UserHit>> {
    return this.page('/api/v1/search/users', q, cursor);
  }

  posts(q: string, cursor: string | null): Promise<CursorPage<PostHit>> {
    return this.page('/api/v1/search/posts', q, cursor);
  }

  groups(q: string, cursor: string | null): Promise<CursorPage<GroupHit>> {
    return this.page('/api/v1/search/groups', q, cursor);
  }

  hashtags(q: string): Promise<HashtagHit[]> {
    return lastValueFrom(this.http.get<HashtagHit[]>('/api/v1/search/hashtags', { params: new HttpParams().set('q', q) }));
  }

  hashtagPosts(tag: string, cursor: string | null): Promise<CursorPage<PostSummaryPublic>> {
    let params = new HttpParams();
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return lastValueFrom(
      this.http.get<CursorPage<PostSummaryPublic>>(`/api/v1/hashtags/${encodeURIComponent(tag)}/posts`, { params }),
    );
  }

  private page<T>(url: string, q: string, cursor: string | null): Promise<CursorPage<T>> {
    let params = new HttpParams().set('q', q);
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return lastValueFrom(this.http.get<CursorPage<T>>(url, { params }));
  }
}
