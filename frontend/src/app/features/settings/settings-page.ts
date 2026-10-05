import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BlocksSettings } from './blocks/blocks';
import { GroupInvitationsSettings } from './group-invitations/group-invitations';
import { ChatInvitationsSettings } from './chat-invitations/chat-invitations';

/**
 * Настройки. Блокировки здесь, потому что заблокированный профиль скрыт и на странице пользователя недоступен.
 * Приглашения в сообщества и в чаты — пока нет отдельного центра уведомлений (TASK-069).
 */
@Component({
  selector: 'app-settings-page',
  imports: [BlocksSettings, GroupInvitationsSettings, ChatInvitationsSettings, RouterLink],
  template: `
    <h1 class="heading">Настройки</h1>
    <a class="security-link" routerLink="/settings/security">Безопасность и устройства</a>
    <app-group-invitations-settings />
    <app-chat-invitations-settings />
    <app-blocks-settings />
  `,
  styles: [`
    :host { display: grid; gap: var(--space-6); }
    .heading { margin: 0; font-size: var(--font-size-h1); line-height: 1.2; }
    .security-link { color: var(--color-primary); font-weight: 600; }
  `],
})
export class SettingsPage {}
