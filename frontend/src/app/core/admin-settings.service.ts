import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export type AppSettingKey = 'registration_open' | 'media_upload_limit_bytes' | 'media_user_quota_bytes' | 'site_name';

/** Значения настроек, которые сервер отдаёт. Ключей, секретов и токенов здесь нет и не будет. */
export interface AppSettingsValues {
  registrationOpen: boolean;
  uploadLimitBytes: number;
  userQuotaBytes: number;
  siteName: string;
}

export interface AppSettingsView {
  values: AppSettingsValues;
  uploadCeilingBytes: number;
  quotaCeilingBytes: number;
}

/** Только allowlist настроек: сервер принимает четыре ключа и отвечает 404 на любой другой. */
@Injectable({ providedIn: 'root' })
export class AdminSettingsService {
  private readonly http = inject(HttpClient);

  get(): Promise<AppSettingsView> {
    return lastValueFrom(this.http.get<AppSettingsView>('/api/v1/admin/settings'));
  }

  update(key: AppSettingKey, value: string | boolean, reason: string): Promise<AppSettingsValues> {
    return lastValueFrom(this.http.put<AppSettingsValues>(`/api/v1/admin/settings/${key}`, { value, reason }));
  }
}
