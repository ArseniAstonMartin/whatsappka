import { Component } from '@angular/core';
import { BlocksSettings } from './blocks/blocks';

/** Настройки. Блокировки здесь, потому что заблокированный профиль скрыт и на странице пользователя недоступен. */
@Component({
  selector: 'app-settings-page',
  imports: [BlocksSettings],
  template: `
    <h1 class="heading">Настройки</h1>
    <app-blocks-settings />
  `,
  styles: [`
    :host { display: grid; gap: var(--space-6); }
    .heading { margin: 0; font-size: var(--font-size-h1); line-height: 1.2; }
  `],
})
export class SettingsPage {}
