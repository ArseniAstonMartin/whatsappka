import { Component, OnInit, inject, signal } from '@angular/core';
import { NotificationPreference, NotificationPreferenceService } from '../../../core/notification-preferences.service';
import { NotificationType } from '../../../core/notification.service';
import { toProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { ToastService } from '../../../shared/ui/toast/toast.service';

/** Названия категорий для пользователя. Текст сообщения и имена собеседников в уведомлениях не показываются. */
const CATEGORY: Record<NotificationType, { title: string; hint: string }> = {
  FOLLOW: { title: 'Новые подписчики', hint: 'Когда на вас подписываются' },
  MESSAGE: { title: 'Новые сообщения', hint: 'Уведомление о сообщении; сам диалог и история не меняются' },
  CHAT_INVITATION: { title: 'Приглашения в чаты', hint: 'Приглашения остаются доступны в настройках' },
  COMMUNITY_INVITATION: { title: 'Приглашения в сообщества', hint: 'Приглашения остаются доступны в настройках' },
  JOIN_REQUEST: { title: 'Заявки в ваши сообщества', hint: 'Для владельцев и администраторов' },
  JOIN_RESULT: { title: 'Решения по вашим заявкам', hint: 'Принята или отклонена заявка в сообщество' },
  COMMENT: { title: 'Комментарии к вашим записям', hint: '' },
  REPLY: { title: 'Ответы на ваши комментарии', hint: '' },
  REACTION: { title: 'Реакции', hint: 'На ваши записи и комментарии' },
  SYSTEM: { title: 'Системные сообщения', hint: 'Важные сведения о работе аккаунта' },
  MODERATION_RESULT: { title: 'Решения по вашим жалобам', hint: 'Сведения о результате рассмотрения' },
  CONTENT_HIDDEN: { title: 'Скрытие вашего материала', hint: 'Сведения о судьбе вашей публикации' },
};

/**
 * Категории уведомлений. Изменение сохраняется на сервере; до ответа переключатель не меняется, при ошибке
 * возвращается прежнее значение и показывается причина. Выключение уведомления не скрывает сами сообщения
 * и приглашения: они остаются в своих разделах.
 */
@Component({
  selector: 'app-notification-settings',
  imports: [AppButton, StatePanel, Skeleton],
  templateUrl: './notification-settings.html',
  styleUrl: './notification-settings.scss',
})
export class NotificationSettings implements OnInit {
  private readonly prefs = inject(NotificationPreferenceService);
  private readonly toasts = inject(ToastService);

  protected readonly items = signal<NotificationPreference[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pending = signal<ReadonlySet<NotificationType>>(new Set());
  protected readonly saveError = signal<string | null>(null);

  ngOnInit(): void {
    void this.load();
  }

  protected title(type: NotificationType): string {
    return CATEGORY[type]?.title ?? type;
  }

  protected hint(type: NotificationType): string {
    return CATEGORY[type]?.hint ?? '';
  }

  protected isPending(type: NotificationType): boolean {
    return this.pending().has(type);
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.items.set(await this.prefs.list());
    } catch (error) {
      this.error.set(toProblem(error).message);
    } finally {
      this.loading.set(false);
    }
  }

  protected async toggle(item: NotificationPreference, input: HTMLInputElement): Promise<void> {
    const enabled = input.checked;
    if (!item.canDisable || this.isPending(item.type)) {
      input.checked = item.enabled;
      return;
    }
    this.saveError.set(null);
    this.pending.update((set) => new Set([...set, item.type]));
    try {
      const saved = await this.prefs.set(item.type, enabled);
      this.items.update((list) => list.map((p) => (p.type === saved.type ? saved : p)));
      this.toasts.show(enabled ? 'Уведомление включено' : 'Уведомление выключено', 'success');
    } catch (error) {
      // Прежнее значение возвращается и в модели, и в самом переключателе: иначе он покажет несохранённое состояние.
      input.checked = item.enabled;
      this.saveError.set(`Не удалось сохранить «${this.title(item.type)}»: ${toProblem(error).message}`);
    } finally {
      this.pending.update((set) => new Set([...set].filter((t) => t !== item.type)));
    }
  }
}
