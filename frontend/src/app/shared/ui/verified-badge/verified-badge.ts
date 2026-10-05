import { Component, input } from '@angular/core';

/**
 * Василёк — маркер проверенного профиля. Не галочка и не используется для лайка, online или успеха.
 * Восемь равных лепестков вокруг компактной середины, доступное имя и подсказка «Проверенный аккаунт».
 */
@Component({
  selector: 'app-verified-badge',
  templateUrl: './verified-badge.html',
  styleUrl: './verified-badge.scss',
  host: {
    'role': 'img',
    'aria-label': 'Проверенный аккаунт',
    'title': 'Проверенный аккаунт',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
  },
})
export class VerifiedBadge {
  readonly size = input<16 | 20 | 24>(20);

  /** Восемь лепестков: эллипсы, повёрнутые на 45° вокруг центра. */
  protected readonly petals = Array.from({ length: 8 }, (_, index) => index * 45);
}
