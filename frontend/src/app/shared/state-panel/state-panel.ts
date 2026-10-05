import { Component, input } from '@angular/core';

export type StatePanelKind = 'loading' | 'empty' | 'error' | 'not-found' | 'forbidden' | 'offline';

/** Единый экран состояния: загрузка, пусто, ошибка, отсутствие раздела, запрет доступа, нет связи. */
@Component({
  selector: 'app-state-panel',
  templateUrl: './state-panel.html',
  styleUrl: './state-panel.scss',
})
export class StatePanel {
  readonly kind = input.required<StatePanelKind>();
  readonly title = input<string>('');
  readonly message = input<string>('');
}
