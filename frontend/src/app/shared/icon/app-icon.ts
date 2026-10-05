import { Component, computed, input } from '@angular/core';

/**
 * Контурные иконки 24×24 с толщиной штриха 1.5 (PRD §8.2). Цвет наследуется через currentColor.
 * Подпись задаётся родителем: без label иконка декоративна и скрыта от screen reader.
 */
const ICON_PATHS = {
  home: ['M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'],
  search: ['M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z', 'M20 20l-4.2-4.2'],
  bell: ['M6 16v-5a6 6 0 1 1 12 0v5l2 2H4z', 'M10 20a2 2 0 0 0 4 0'],
  chat: ['M4 5h16v11H9l-5 4z'],
  users: [
    'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z',
    'M3 20a6 6 0 0 1 12 0',
    'M16 4.5a3.5 3.5 0 0 1 0 6.5',
    'M18 20a6 6 0 0 0-2.5-4.9',
  ],
  user: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 20a8 8 0 0 1 16 0'],
  settings: [
    'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z',
    'M19.4 13.5a7.6 7.6 0 0 0 0-3l2-1.5-2-3.4-2.4 1a7.6 7.6 0 0 0-2.6-1.5L14 3h-4l-.4 2.6A7.6 7.6 0 0 0 7 7.1l-2.4-1-2 3.4 2 1.5a7.6 7.6 0 0 0 0 3l-2 1.5 2 3.4 2.4-1a7.6 7.6 0 0 0 2.6 1.5L10 21h4l.4-2.6a7.6 7.6 0 0 0 2.6-1.5l2.4 1 2-3.4z',
  ],
  plus: ['M12 5v14', 'M5 12h14'],
  close: ['M6 6l12 12', 'M18 6L6 18'],
  edit: ['M4 20h4L19 9l-4-4L4 16z', 'M13.5 6.5l4 4'],
  trash: ['M4 7h16', 'M9 7V4h6v3', 'M6 7l1 13h10l1-13'],
  shield: ['M12 3l8 3v6c0 4.5-3.4 8-8 9-4.6-1-8-4.5-8-9V6z'],
  logout: ['M14 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4', 'M10 8l-4 4 4 4', 'M6 12h9'],
  lock: ['M6 11h12v9H6z', 'M8.5 11V8a3.5 3.5 0 0 1 7 0v3'],
  file: ['M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z', 'M14 3v5h5'],
} as const;

export type AppIconName = keyof typeof ICON_PATHS;

@Component({
  selector: 'app-icon',
  templateUrl: './app-icon.html',
  host: {
    '[attr.role]': 'label() ? "img" : null',
    '[attr.aria-label]': 'label()',
    '[attr.aria-hidden]': 'label() ? null : "true"',
    'class': 'app-icon',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
  },
})
export class AppIcon {
  readonly name = input.required<AppIconName>();
  readonly size = input<20 | 24>(24);
  readonly label = input<string | null>(null);

  protected readonly paths = computed<readonly string[]>(() => ICON_PATHS[this.name()]);
}
