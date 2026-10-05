import { Component, OnInit, inject, signal } from '@angular/core';
import { GroupChatInvitation, GroupChatService } from '../../../core/group-chat.service';
import { toProblem } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';

/**
 * Входящие приглашения в групповые чаты (TASK-062): принять/отклонить может только получатель.
 * Отдельного центра уведомлений ещё нет, поэтому это часть настроек, как и приглашения в сообщества.
 */
@Component({
  selector: 'app-chat-invitations-settings',
  imports: [AppButton, StatePanel, Skeleton],
  templateUrl: './chat-invitations.html',
  styleUrl: './chat-invitations.scss',
})
export class ChatInvitationsSettings implements OnInit {
  private readonly groups = inject(GroupChatService);
  private readonly toasts = inject(ToastService);

  protected readonly items = signal<GroupChatInvitation[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busyIds = signal<ReadonlySet<string>>(new Set());

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.items.set(await this.groups.myInvitations());
    } catch (error) {
      this.error.set(toProblem(error).message);
    } finally {
      this.loading.set(false);
    }
  }

  protected isBusy(id: string): boolean {
    return this.busyIds().has(id);
  }

  protected expiresLabel(iso: string): string {
    return new Date(iso).toLocaleString('ru-RU', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' });
  }

  protected async accept(invitation: GroupChatInvitation): Promise<void> {
    if (this.isBusy(invitation.id)) {
      return;
    }
    this.busyIds.update((ids) => new Set([...ids, invitation.id]));
    try {
      await this.groups.acceptInvitation(invitation.id);
      this.items.update((list) => list.filter((i) => i.id !== invitation.id));
      this.toasts.show(`Вы вступили в «${invitation.conversationTitle}»`, 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.busyIds.update((ids) => {
        const next = new Set(ids);
        next.delete(invitation.id);
        return next;
      });
    }
  }

  protected async decline(invitation: GroupChatInvitation): Promise<void> {
    if (this.isBusy(invitation.id)) {
      return;
    }
    this.busyIds.update((ids) => new Set([...ids, invitation.id]));
    try {
      await this.groups.declineInvitation(invitation.id);
      this.items.update((list) => list.filter((i) => i.id !== invitation.id));
      this.toasts.show('Приглашение отклонено');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.busyIds.update((ids) => {
        const next = new Set(ids);
        next.delete(invitation.id);
        return next;
      });
    }
  }
}
