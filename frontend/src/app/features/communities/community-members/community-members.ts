import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CommunityJoinRequestItem,
  CommunityMember,
  CommunityRole,
  CommunityService,
} from '../../../core/community.service';
import { ProfileService } from '../../../core/profile.service';
import { toProblem } from '../../../core/api-error';
import { AuthService } from '../../../core/auth.service';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Card } from '../../../shared/ui/card/card';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { TextField } from '../../../shared/ui/text-field/text-field';

const ROLE_LABEL: Record<CommunityRole, string> = { OWNER: 'Владелец', ADMIN: 'Администратор', MEMBER: 'Участник' };

/**
 * Управление составом сообщества (TASK-040): роли, исключение, передача владения, заявки на
 * вступление и приглашение по имени пользователя. Виден только OWNER/ADMIN — сервер проверяет права
 * заново на каждое действие, здесь только скрытие недоступных кнопок.
 */
@Component({
  selector: 'app-community-members',
  imports: [ReactiveFormsModule, AppButton, Card, Skeleton, StatePanel, TextField],
  templateUrl: './community-members.html',
  styleUrl: './community-members.scss',
})
export class CommunityMembers implements OnInit {
  private readonly communities = inject(CommunityService);
  private readonly profiles = inject(ProfileService);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  readonly groupId = input.required<string>();
  readonly viewerRole = input.required<CommunityRole>();
  /** Сообщает родителю, что состав или собственная роль могли измениться (счётчик, вид карточки). */
  readonly changed = output<void>();

  protected readonly isOwner = computed(() => this.viewerRole() === 'OWNER');
  protected readonly myId = computed(() => this.auth.user()?.id ?? null);

  protected readonly members = signal<CommunityMember[]>([]);
  protected readonly membersLoading = signal(true);
  protected readonly membersError = signal<string | null>(null);
  protected readonly busyMemberIds = signal<ReadonlySet<string>>(new Set());

  protected readonly requests = signal<CommunityJoinRequestItem[]>([]);
  protected readonly requestsLoading = signal(true);
  protected readonly requestsError = signal<string | null>(null);
  protected readonly busyRequestIds = signal<ReadonlySet<string>>(new Set());

  protected readonly inviteForm = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly inviteBusy = signal(false);
  protected readonly inviteError = signal<string | null>(null);

  ngOnInit(): void {
    void this.loadMembers();
    void this.loadRequests();
  }

  protected roleLabel(role: CommunityRole): string {
    return ROLE_LABEL[role] ?? role;
  }

  protected canRemove(member: CommunityMember): boolean {
    if (member.id === this.myId() || member.role === 'OWNER') {
      return false;
    }
    return this.isOwner() || member.role === 'MEMBER';
  }

  protected canChangeRole(member: CommunityMember): boolean {
    return this.isOwner() && member.id !== this.myId() && member.role !== 'OWNER';
  }

  protected canTransferTo(member: CommunityMember): boolean {
    return this.isOwner() && member.id !== this.myId() && member.role !== 'OWNER';
  }

  private async loadMembers(): Promise<void> {
    this.membersLoading.set(true);
    this.membersError.set(null);
    try {
      this.members.set(await this.communities.members(this.groupId()));
    } catch (error) {
      this.membersError.set(toProblem(error).message);
    } finally {
      this.membersLoading.set(false);
    }
  }

  private async loadRequests(): Promise<void> {
    this.requestsLoading.set(true);
    this.requestsError.set(null);
    try {
      this.requests.set(await this.communities.pendingJoinRequests(this.groupId()));
    } catch (error) {
      this.requestsError.set(toProblem(error).message);
    } finally {
      this.requestsLoading.set(false);
    }
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

  protected isMemberBusy(id: string): boolean {
    return this.busyMemberIds().has(id);
  }

  protected async removeMember(member: CommunityMember): Promise<void> {
    if (this.isMemberBusy(member.id)) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: `Исключить ${member.displayName}?`,
      message: 'Участник потеряет доступ к закрытому составу и сможет вернуться только по новой заявке или приглашению.',
      confirmLabel: 'Исключить',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    try {
      await this.withBusyMember(member.id, () => this.communities.removeMember(this.groupId(), member.id));
      this.members.update((list) => list.filter((m) => m.id !== member.id));
      this.toasts.show(`${member.displayName} исключён`, 'success');
      this.changed.emit();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }

  protected async setRole(member: CommunityMember, role: 'ADMIN' | 'MEMBER'): Promise<void> {
    if (this.isMemberBusy(member.id) || member.role === role) {
      return;
    }
    try {
      await this.withBusyMember(member.id, () => this.communities.setMemberRole(this.groupId(), member.id, role));
      this.members.update((list) => list.map((m) => (m.id === member.id ? { ...m, role } : m)));
      this.toasts.show(role === 'ADMIN' ? `${member.displayName} назначен администратором` : `${member.displayName} теперь участник`, 'success');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }

  protected async transferOwnership(member: CommunityMember): Promise<void> {
    if (this.isMemberBusy(member.id)) {
      return;
    }
    const confirmed = await this.confirm.confirm({
      title: `Передать владение ${member.displayName}?`,
      message: 'Вы станете администратором, а управление сообществом перейдёт новому владельцу. Действие нельзя отменить из интерфейса.',
      confirmLabel: 'Передать владение',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    try {
      await this.withBusyMember(member.id, () => this.communities.transferOwnership(this.groupId(), member.id));
      this.toasts.show(`Владение передано: ${member.displayName}`, 'success');
      await this.loadMembers();
      this.changed.emit();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }

  protected isRequestBusy(id: string): boolean {
    return this.busyRequestIds().has(id);
  }

  private withBusyRequest<T>(id: string, action: () => Promise<T>): Promise<T> {
    this.busyRequestIds.update((ids) => new Set([...ids, id]));
    return action().finally(() => {
      this.busyRequestIds.update((ids) => {
        const next = new Set(ids);
        next.delete(id);
        return next;
      });
    });
  }

  protected async acceptRequest(request: CommunityJoinRequestItem): Promise<void> {
    if (this.isRequestBusy(request.id)) {
      return;
    }
    try {
      await this.withBusyRequest(request.id, () => this.communities.acceptJoinRequest(this.groupId(), request.id));
      this.requests.update((list) => list.filter((r) => r.id !== request.id));
      this.toasts.show(`${request.displayName} принят в сообщество`, 'success');
      await this.loadMembers();
      this.changed.emit();
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
    }
  }

  protected async rejectRequest(request: CommunityJoinRequestItem): Promise<void> {
    if (this.isRequestBusy(request.id)) {
      return;
    }
    try {
      await this.withBusyRequest(request.id, () => this.communities.rejectJoinRequest(this.groupId(), request.id));
      this.requests.update((list) => list.filter((r) => r.id !== request.id));
      this.toasts.show('Заявка отклонена');
    } catch (error) {
      this.toasts.show(toProblem(error).message, 'error');
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
      await this.communities.invite(this.groupId(), target.id);
      this.toasts.show(`Приглашение отправлено: ${target.displayName}`, 'success');
      this.inviteForm.reset({ username: '' });
    } catch (error) {
      const problem = toProblem(error);
      this.inviteError.set(problem.status === 404 ? 'Пользователь с таким именем не найден' : problem.message);
    } finally {
      this.inviteBusy.set(false);
    }
  }
}
