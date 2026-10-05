import { Component, input } from '@angular/core';

/** Заглушка загрузки. Скрыта от screen reader; состояние загрузки объявляет родитель. */
@Component({
  selector: 'app-skeleton',
  template: '',
  styleUrl: './skeleton.scss',
  host: {
    'aria-hidden': 'true',
    '[style.width]': 'width()',
    '[style.height]': 'height()',
    '[style.border-radius]': 'radius()',
  },
})
export class Skeleton {
  readonly width = input<string>('100%');
  readonly height = input<string>('16px');
  readonly radius = input<string>('var(--radius-control)');
}
