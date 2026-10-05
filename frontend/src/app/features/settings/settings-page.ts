import { Component } from '@angular/core';
import { BlocksSettings } from './blocks/blocks';
import { GroupInvitationsSettings } from './group-invitations/group-invitations';

/**
 * Настройки. Блокировки здесь, потому что заблокированный профиль скрыт и на странице пользователя недоступен.
 * Приглашения в сообщества — пока нет отдельного центра уведомлений (TASK-069).
 */
@Component({
  selector: 'app-settings-page',
  imports: [BlocksSettings, GroupInvitationsSettings],
  template: `
    <h1 class="heading">Настройки</h1>
    <app-group-invitations-settings />
    <app-blocks-settings />
  `,
  styles: [`
    :host { display: grid; gap: var(--space-6); }
    .heading { margin: 0; font-size: var(--font-size-h1); line-height: 1.2; }
  `],
})
export class SettingsPage {}
