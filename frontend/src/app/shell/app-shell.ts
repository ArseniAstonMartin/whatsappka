import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AppIcon, AppIconName } from '../shared/icon/app-icon';
import { AuthService } from '../core/auth.service';
import { ConnectionState } from '../core/connection-state';
import { AppButton } from '../shared/ui/button/app-button';
import { CurrentUser } from '../core/current-user';

export interface NavItem {
  path: string;
  label: string;
  icon: AppIconName;
  /** Раздел основной нижней панели на телефоне. Остальные доступны из верхней панели. */
  primary: boolean;
  /** Если задано, раздел виден только при одной из ролей. */
  roles?: readonly string[];
}

export const NAV_ITEMS: readonly NavItem[] = [
  { path: 'feed', label: 'Лента', icon: 'home', primary: true },
  { path: 'search', label: 'Поиск', icon: 'search', primary: true },
  { path: 'groups', label: 'Группы', icon: 'users', primary: true },
  { path: 'chats', label: 'Сообщения', icon: 'chat', primary: true },
  { path: 'profile', label: 'Профиль', icon: 'user', primary: true },
  { path: 'settings', label: 'Настройки', icon: 'settings', primary: false },
  { path: 'manage', label: 'Управление', icon: 'shield', primary: false, roles: ['MODERATOR', 'ADMIN'] },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AppIcon, AppButton],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  private readonly user = inject(CurrentUser);
  protected readonly connection = inject(ConnectionState);
  private readonly auth = inject(AuthService);

  protected readonly visibleItems = computed(() =>
    NAV_ITEMS.filter((item) => !item.roles || this.user.hasAnyRole(item.roles)),
  );
  protected readonly primaryItems = computed(() => this.visibleItems().filter((item) => item.primary));
  protected readonly secondaryItems = computed(() => this.visibleItems().filter((item) => !item.primary));

  protected logout(): void {
    void this.auth.logout();
  }
}
