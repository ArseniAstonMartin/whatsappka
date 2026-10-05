import { Component, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

/** Кнопка с общими токенами. Минимальная область нажатия 44 px. Применяется к нативному button. */
@Component({
  selector: 'button[appButton]',
  template: '<ng-content />',
  styleUrl: './app-button.scss',
  host: {
    '[class]': '"app-button app-button-" + variant()',
    '[attr.aria-busy]': 'busy() ? "true" : null',
    '[disabled]': 'disabled() || busy()',
    'type': 'button',
  },
})
export class AppButton {
  readonly variant = input<ButtonVariant>('primary');
  readonly busy = input<boolean>(false);
  readonly disabled = input<boolean>(false);
}
