import { Component, input, output } from '@angular/core';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { AppButton } from '../button/app-button';

export interface MenuItem {
  label: string;
  danger?: boolean;
  disabled?: boolean;
}

/** Меню действий: клавиатурная навигация стрелками и Esc обеспечивает CDK Menu. */
@Component({
  selector: 'app-menu-button',
  imports: [AppButton, CdkMenu, CdkMenuItem, CdkMenuTrigger],
  templateUrl: './menu-button.html',
  styleUrl: './menu-button.scss',
})
export class MenuButton {
  readonly label = input.required<string>();
  readonly items = input.required<readonly MenuItem[]>();
  readonly selected = output<number>();
}
