import { Component } from '@angular/core';

/** Карточка: поверхность без чисто белого фона, мягкая тень, скругления карточки (20 px). */
@Component({
  selector: 'app-card',
  template: '<ng-content />',
  styleUrl: './card.scss',
})
export class Card {}
