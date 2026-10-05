import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export interface OwnProfile {
  id: string;
  username: string;
  email: string;
  displayName: string;
  bio: string;
  statusText: string;
  timezone: string;
  verified: boolean;
  avatarMediaId: string | null;
  coverMediaId: string | null;
}

export interface PublicProfile {
  id: string;
  username: string;
  displayName: string;
  bio: string;
  statusText: string;
  verified: boolean;
  avatarMediaId: string | null;
  coverMediaId: string | null;
}

export interface Relations {
  followers: number;
  following: number;
  followedByViewer: boolean;
  self: boolean;
}

export interface ProfilePatch {
  displayName?: string;
  bio?: string;
  statusText?: string;
  timezone?: string;
}

/** Запросы профиля. Списки связей и блокировки подключаются в отдельных задачах. */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  own(): Promise<OwnProfile> {
    return lastValueFrom(this.http.get<OwnProfile>('/api/v1/me/profile'));
  }

  patch(body: ProfilePatch): Promise<OwnProfile> {
    return lastValueFrom(this.http.patch<OwnProfile>('/api/v1/me/profile', body));
  }

  publicByUsername(username: string): Promise<PublicProfile> {
    return lastValueFrom(this.http.get<PublicProfile>(`/api/v1/users/${encodeURIComponent(username)}`));
  }

  relations(userId: string): Promise<Relations> {
    return lastValueFrom(this.http.get<Relations>(`/api/v1/users/${userId}/relations`));
  }

  setFollowing(userId: string, following: boolean): Promise<void> {
    const request = following
      ? this.http.put(`/api/v1/users/${userId}/follow`, null)
      : this.http.delete(`/api/v1/users/${userId}/follow`);
    return lastValueFrom(request).then(() => undefined);
  }

  attach(mediaId: string, linkType: 'PROFILE_AVATAR' | 'PROFILE_COVER', linkId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/media/${mediaId}/attach`, { linkType, linkId })).then(() => undefined);
  }

  detach(mediaId: string, linkType: 'PROFILE_AVATAR' | 'PROFILE_COVER', linkId: string): Promise<void> {
    return lastValueFrom(this.http.post(`/api/v1/media/${mediaId}/detach`, { linkType, linkId })).then(() => undefined);
  }
}
