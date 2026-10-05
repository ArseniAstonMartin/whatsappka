import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ChatService, Conversation } from '../../../core/chat.service';
import { GroupChatMember, GroupChatRole, GroupChatService } from '../../../core/group-chat.service';
import { ProfileService } from '../../../core/profile.service';
import { AuthService } from '../../../core/auth.service';
import { toProblem } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Avatar } from '../../../shared/ui/avatar/avatar';
import { Card } from '../../../shared/ui/card/card';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { MediaUpload, UploadedMedia } from '../../../shared/media/media-upload/media-upload';
import { MediaView } from '../../../shared/media/media-view/media-view';

const ROLE_LABEL: Record<GroupChatRole, string> = { OWNER: 'Владелец', ADMIN: 'Администратор', MEMBER: 'Участник' };

/**
 * Управление групповым чатом (TASK-062): название и аватар меняют владелец и администраторы, они
 * же приглашают и управляют составом; выйти может любой, кроме владельца — тот сперва передаёт
 * владение. Сервер проверяет права заново на каждое действие, здесь только скрытие недоступных кнопок.
 */
@Component({
  selector: 'app-group-chat-settings',
  imports: [ReactiveFormsModule, RouterLink, AppButton, Avatar, Card, Skeleton, StatePanel, TextField, MediaUpload, MediaView],
  templateUrl: './group-chat-settings.html',
  styleUrl: './group-chat-settings.scss',
})
export class GroupChatSettings implements OnInit {
  private readonly chats = inject(ChatService);
  private readonly groups = inject(GroupChatService);
  private readonly profiles = inject(ProfileService);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly router = inject(Router);

  readonly id = input.required<string>();

  protected readonly conversation = signal<Conversation | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly members = signal<GroupChatMember[]>([]);
  protected readonly membersLoading = signal(true);
  protected readonly membersError = signal<string | null>(null);
  protected readonly busyMemberIds = signal<ReadonlySet<string>>(new Set());

  protected readonly myId = computed(() => this.auth.user()?.id ?? null);
  protected readonly myRole = computed<GroupChatRole | null>(() => {
    const me = this.myId();
    return this.members().find((m) => m.id === me)?.role ?? null;
  });
  protected readonly isOwner = computed(() => this.myRole() === 'OWNER');
  protected readonly canEditSettings = computed(() => this.myRole() === 'OWNER' || this.myRole() === 'ADMIN');
  protected readonly canLeave = computed(() => this.myRole() !== null && this.myRole() !== 'OWNER');

  protected readonly titleForm = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
  });
  protected readonly renaming = signal(false);
  protected readonly renameError = signal<string | null>(null);

  protected readonly avatarBusy = signal(false);

  protected readonly inviteForm = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly inviteBusy = signal(false);
  protected readonly inviteError = signal<string | null>(null);

  protected readonly leaving = signal(false);

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    try {
      const chat = await this.chats.get(this.id());
      if (chat.type !== 'GROUP') {
        this.notFound.set(true);
        return;
      }
      this.conversation.set(chat);
      this.titleForm.setValue({ title: chat.title ?? '' });
      await this.loadMembers();
    } catch (error) {
      const problem = toProblem(error);
      if (problem.status === 404) {
        this.notFound.set(true);
      } else {
        this.error.set(problem.message);
      }
    } finally {
      this.loading.set(false);
    }
  }

  private async loadMembers(): Promise<void> {
    this.membersLoading.set(true);
    this.membersError.set(null);
    try {
      this.members.set(await this.groups.members(this.id()));
    } catch (error) {
      this.membersError.set(toProblem(error).message);
    } finally {
      this.membersLoading.set(false);
    }
  }

  protected roleLabel(role: GroupChatRole): string {
    return ROLE_LABEL[role] ?? role;
  }

  protected canRemove(member: GroupChatMember): boolean {
    if (member.id === this.myId() || member.role === 'OWNER') {
      return false;
    }
    return this.isOwner() || member.role === 'MEMBER';
  }

  protected canChangeRole(member: GroupChatMember): boolean {
    return this.isOwner() && member.id !== this.myId() && member.role !== 'OWNER';
  }

  protected canTransferTo(member: GroupChatMember): boolean {
    return this.isOwner() && member.id !== this.myId() && member.role !== 'OWNER';
  }

  protected isMemberBusy(id: string): boolean {
    return this.busyMemberIds().has(id);
  }

  private withBusyMember<T>(id: string, action: () => Promise<T>): Promise<T> {
    this.busyMemberIds.update((ids) => new Set([...ids, id]));
    return action().finally(() => {
      this.busyMemberIds.update((ids) => {
        const next = new Set(ids);
        next.delete(id);
        return next;
      });
    });
  }

  protected async rename(): Promise<void> {
    if (this.titleForm.invalid || this.renaming()) {
      this.titleForm.markAllAsTouched();
      return;
    }
    this.renaming.set(true);
    this.renameError.set(null);
    try {
      const title = this.titleForm.controls.title.value.trim();
      await this.groups.rename(this.id(), title);
      this.conversation.update((c) => (c ? { ...c, title } : c));
      this.toasts.show('Название обновлено', 'success');
    } catch (error) {
      this.renameError.set(toProblem(error).message);
    } finally {
      this.renaming.set(false);
    }
  }

  protected async changeAvatar(media: UploadedMedia): Promise<void> {
    this.avatarBusy.set(true);
    try {
      await this.groups.changeAvatar(this.id(), media.id);
      this.conversation.update((c) => (c ? { ...c, avatarMediaId: media.id } : c));
      this.toasts.show('Аватар обновлён', 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.avatarBusy.set(false);
    }
  }

  /** Приглашение ищет пользователя по имени: своего UUID форма не принимает и не показывает. */
  protected async invite(): Promise<void> {
    if (this.inviteForm.invalid || this.inviteBusy()) {
      this.inviteForm.markAllAsTouched();
      return;
    }
    const username = this.inviteForm.controls.username.value.trim();
    this.inviteBusy.set(true);
    this.inviteError.set(null);
    try {
      const target = await this.profiles.publicByUsername(username);
      await this.groups.invite(this.id(), target.id);
      this.toasts.show(`Приглашение отправлено: ${target.displayName}`, 'success');
      this.inviteForm.reset({ username: '' });
    } catch (error) {
      const problem = toProblem(error);
      this.inviteError.set(problem.status === 404 ? 'Пользователь с таким именем не найден' : problem.message);
    } finally {
      this.inviteBusy.set(false);
    }
  }

  protected async removeMember(member: GroupChatMember): Promise<void> {
    if (this.isMemberBusy(member.id)) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: `Исключить ${member.displayName}?`,
      message: 'Участник потеряет доступ к истории чата с момента исключения.',
      confirmLabel: 'Исключить',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    try {
      await this.withBusyMember(member.id, () => this.groups.removeMember(this.id(), member.id));
      this.members.update((list) => list.filter((m) => m.id !== member.id));
      this.toasts.show(`${member.displayName} исключён`, 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }

  protected async setRole(member: GroupChatMember, role: 'ADMIN' | 'MEMBER'): Promise<void> {
    if (this.isMemberBusy(member.id) || member.role === role) {
      return;
    }
    try {
      await this.withBusyMember(member.id, () => this.groups.setMemberRole(this.id(), member.id, role));
      this.members.update((list) => list.map((m) => (m.id === member.id ? { ...m, role } : m)));
      this.toasts.show(
        role === 'ADMIN' ? `${member.displayName} назначен администратором` : `${member.displayName} теперь участник`,
        'success',
      );
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }

  protected async transferOwnership(member: GroupChatMember): Promise<void> {
    if (this.isMemberBusy(member.id)) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: `Передать владение ${member.displayName}?`,
      message: 'Вы станете администратором, а управление чатом перейдёт новому владельцу. Действие нельзя отменить из интерфейса.',
      confirmLabel: 'Передать владение',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    try {
      await this.withBusyMember(member.id, () => this.groups.transferOwnership(this.id(), member.id));
      this.toasts.show(`Владение передано: ${member.displayName}`, 'success');
      await this.loadMembers();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }

  protected async leave(): Promise<void> {
    if (this.leaving() || !this.canLeave()) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: 'Покинуть чат?',
      message: 'Доступ к истории и новым сообщениям пропадёт сразу. Вернуться можно только по новому приглашению.',
      confirmLabel: 'Покинуть чат',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.leaving.set(true);
    try {
      await this.groups.leave(this.id());
      this.toasts.show('Вы покинули чат', 'success');
      await this.router.navigate(['/chats']);
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    } finally {
      this.leaving.set(false);
    }
  }
}
