import { Component, inject } from '@angular/core';
import { AppIcon } from '../../icon/app-icon';
import { ToastService } from './toast.service';

/** Контейнер уведомлений: успех и информация — status, ошибка — alert. */
@Component({
  selector: 'app-toast-host',
  imports: [AppIcon],
  templateUrl: './toast-host.html',
  styleUrl: './toast-host.scss',
})
export class ToastHost {
  private readonly service = inject(ToastService);

  protected readonly toasts = this.service.toasts;

  protected dismiss(id: number): void {
    this.service.dismiss(id);
  }
}
