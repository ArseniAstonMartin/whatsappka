import {
  Component,
  ElementRef,
  Injector,
  computed,
  effect,
  afterNextRender,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ChatMessage, ChatService, Conversation } from '../../../core/chat.service';
import { GroupChatMember, GroupChatRole, GroupChatService } from '../../../core/group-chat.service';
import { ProfileService } from '../../../core/profile.service';
import { RealtimeEvent, RealtimeService } from '../../../core/realtime/realtime.service';
import { toProblem, messageForCode } from '../../../core/api-error';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Avatar } from '../../../shared/ui/avatar/avatar';
import { MenuButton, MenuItem } from '../../../shared/ui/menu-button/menu-button';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { MediaUpload, UploadedMedia } from '../../../shared/media/media-upload/media-upload';
import { MediaView } from '../../../shared/media/media-view/media-view';
import { MediaPurpose } from '../../../shared/media/purposes';

const EDIT_WINDOW_MS = 24 * 60 * 60 * 1000;

const PURPOSE_LABELS: Record<string, string> = {
  CHAT_IMAGE: 'Изображение',
  CHAT_DOCUMENT: 'Документ',
};

const ATTACHMENTS_MAX = 5;

type SendStatus = 'pending' | 'saved' | 'error';

interface AttachSlot {
  slotId: string;
  purpose: MediaPurpose;
}

interface PendingAttachment {
  mediaId: string;
  purpose: string;
}

interface PendingMessage {
  clientMessageId: string;
  body: string | null;
  attachments: PendingAttachment[];
  status: SendStatus;
  errorText: string | null;
  createdAt: string;
}

interface AckPayload {
  clientMessageId: string;
  messageId: string;
  seq: number;
}

interface FailedPayload {
  clientMessageId: string;
  code: string;
}

interface EditedPayload {
  messageId: string;
  body: string | null;
  version: number;
  updatedAt: string;
}

interface DeletedPayload {
  messageId: string;
  version: number;
  updatedAt: string;
}

/**
 * История диалога — личного или группового (TASK-061, TASK-062) — отправка, правка и удаление
 * сообщений (TASK-063). Сервер отдаёт страницы от новых к старым; здесь они выводятся снизу вверх.
 * Подгрузка более старых страниц сохраняет место просмотра: высота, добавленная сверху, компенсируется
 * прокруткой.
 *
 * <p>Отправленное сообщение сперва живёт в {@link pending} с локальным client_message_id; подтверждение
 * (`message.saved`) переносит его в {@link messages} как единственный пузырь — второй не появляется.
 * Входящее `message.created` от другого участника только сигнализирует и не несёт содержимого, поэтому
 * список догружается тем же REST-запросом, что и история. Правка и удаление приходят WS-событием с
 * версией — применяются только если она новее уже показанной, поэтому собственное REST-действие и его
 * же эхо по WebSocket не задваивают изменение.
 */
@Component({
  selector: 'app-chat-dialog',
  imports: [
    ReactiveFormsModule,
    AppButton,
    Avatar,
    MenuButton,
    Skeleton,
    StatePanel,
    RouterLink,
    TextField,
    MediaUpload,
    MediaView,
  ],
  templateUrl: './chat-dialog.html',
  styleUrl: './chat-dialog.scss',
})
export class ChatDialog {
  private readonly chats = inject(ChatService);
  private readonly groupChats = inject(GroupChatService);
  private readonly profiles = inject(ProfileService);
  private readonly realtime = inject(RealtimeService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly injector = inject(Injector);

  readonly id = input.required<string>();

  protected readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  protected readonly conversation = signal<Conversation | null>(null);
  protected readonly members = signal<GroupChatMember[]>([]);
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly pending = signal<PendingMessage[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly selfId = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadingOlder = signal(false);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly memberNames = computed<Record<string, string>>(() => {
    const names: Record<string, string> = {};
    for (const member of this.members()) {
      names[member.id] = member.displayName;
    }
    return names;
  });
  protected readonly myGroupRole = computed<GroupChatRole | null>(() => {
    const me = this.selfId();
    return this.members().find((m) => m.id === me)?.role ?? null;
  });

  protected readonly textControl = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(4000)] });
  protected readonly attachSlots = signal<AttachSlot[]>([]);
  protected readonly attachReady = signal<Record<string, UploadedMedia>>({});
  protected readonly attachmentsMax = ATTACHMENTS_MAX;

  protected readonly editingId = signal<string | null>(null);
  protected readonly editControl = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(4000)] });
  protected readonly editBusy = signal(false);
  protected readonly editError = signal<string | null>(null);

  protected readonly reasonPromptId = signal<string | null>(null);
  protected readonly reasonControl = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] });
  protected readonly reasonBusy = signal(false);
  protected readonly reasonError = signal<string | null>(null);

  private loadToken = 0;

  constructor() {
    effect(() => {
      const conversationId = this.id();
      untracked(() => void this.start(conversationId));
    });
    this.realtime.events$.pipe(takeUntilDestroyed()).subscribe((event) => this.onRealtimeEvent(event));
  }

  protected async start(conversationId: string): Promise<void> {
    const token = ++this.loadToken;
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    this.messages.set([]);
    this.pending.set([]);
    try {
      const [conversation, own, page] = await Promise.all([
        this.chats.get(conversationId),
        this.selfId() ? Promise.resolve(null) : this.profiles.own(),
        this.chats.history(conversationId, null),
      ]);
      if (token !== this.loadToken) {
        return;
      }
      if (own) {
        this.selfId.set(own.id);
      }
      this.conversation.set(conversation);
      this.messages.set(oldestFirst(page.items));
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
      if (conversation.type === 'GROUP') {
        void this.loadMembers(conversationId);
      }
      afterNextRender(() => this.scrollToEnd(), { injector: this.injector });
    } catch (error) {
      if (token !== this.loadToken) {
        return;
      }
      const problem = toProblem(error);
      if (problem.status === 404) {
        this.notFound.set(true);
      } else {
        this.error.set(problem.message);
      }
    } finally {
      if (token === this.loadToken) {
        this.loading.set(false);
      }
    }
  }

  protected async loadOlder(): Promise<void> {
    const cursor = this.nextCursor();
    const conversationId = this.id();
    const scroller = this.scroller()?.nativeElement;
    if (this.loadingOlder() || !this.hasMore() || !cursor || !scroller) {
      return;
    }
    const heightBefore = scroller.scrollHeight;
    const topBefore = scroller.scrollTop;
    this.loadingOlder.set(true);
    try {
      const page = await this.chats.history(conversationId, cursor);
      if (conversationId !== this.id()) {
        return;
      }
      const seen = new Set(this.messages().map((message) => message.id));
      const older = oldestFirst(page.items).filter((message) => !seen.has(message.id));
      this.messages.set([...older, ...this.messages()]);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
      afterNextRender(
        () => {
          scroller.scrollTop = scroller.scrollHeight - heightBefore + topBefore;
        },
        { injector: this.injector },
      );
    } catch (error) {
      this.error.set(toProblem(error).message);
    } finally {
      this.loadingOlder.set(false);
    }
  }

  protected isOwn(message: ChatMessage): boolean {
    return message.senderId === this.selfId();
  }

  /** Имя отправителя подписывается только в групповом чате — в личном диалоге собеседник один. */
  protected senderName(message: ChatMessage): string | null {
    if (this.conversation()?.type !== 'GROUP' || this.isOwn(message)) {
      return null;
    }
    return this.memberNames()[message.senderId] ?? null;
  }

  protected edited(message: ChatMessage): boolean {
    return !message.deleted && new Date(message.updatedAt).getTime() > new Date(message.createdAt).getTime();
  }

  private canEditMessage(message: ChatMessage): boolean {
    if (!this.isOwn(message) || message.deleted) {
      return false;
    }
    return Date.now() - new Date(message.createdAt).getTime() <= EDIT_WINDOW_MS;
  }

  private canDeleteMessage(message: ChatMessage): boolean {
    if (message.deleted) {
      return false;
    }
    if (this.isOwn(message)) {
      return true;
    }
    const role = this.myGroupRole();
    return this.conversation()?.type === 'GROUP' && (role === 'OWNER' || role === 'ADMIN');
  }

  private actionsFor(message: ChatMessage): { label: string; danger?: boolean; run: () => void }[] {
    const actions: { label: string; danger?: boolean; run: () => void }[] = [];
    if (this.canEditMessage(message)) {
      actions.push({ label: 'Редактировать', run: () => this.toggleEdit(message) });
    }
    if (this.canDeleteMessage(message)) {
      actions.push({ label: 'Удалить', danger: true, run: () => this.requestDelete(message) });
    }
    return actions;
  }

  protected menuItems(message: ChatMessage): MenuItem[] {
    return this.actionsFor(message).map(({ label, danger }) => ({ label, danger }));
  }

  protected onMenuAction(message: ChatMessage, index: number): void {
    this.actionsFor(message)[index]?.run();
  }

  protected toggleEdit(message: ChatMessage): void {
    if (this.editingId() === message.id) {
      this.editingId.set(null);
      return;
    }
    this.reasonPromptId.set(null);
    this.editingId.set(message.id);
    this.editControl.setValue(message.body ?? '');
    this.editError.set(null);
  }

  /** Ошибка правки не сбрасывает форму: введённый текст остаётся, пока правку не доведут до конца. */
  protected async submitEdit(message: ChatMessage): Promise<void> {
    if (this.editBusy()) {
      return;
    }
    const body = this.editControl.value.trim() || null;
    this.editBusy.set(true);
    this.editError.set(null);
    try {
      const edited = await this.chats.editMessage(this.id(), message.id, body);
      this.applyEdited({ messageId: edited.id, body, version: edited.version, updatedAt: edited.updatedAt });
      this.editingId.set(null);
    } catch (error) {
      this.editError.set(toProblem(error).message);
    } finally {
      this.editBusy.set(false);
    }
  }

  /** Своё сообщение удаляется сразу после подтверждения; чужое в групповом чате требует причины. */
  protected async requestDelete(message: ChatMessage): Promise<void> {
    if (this.isOwn(message)) {
      const confirmed = await this.confirm.confirm({
        title: 'Удалить сообщение?',
        message: 'Сообщение заменится заглушкой для всех участников. Действие нельзя отменить из интерфейса.',
        confirmLabel: 'Удалить',
        danger: true,
      });
      if (!confirmed) {
        return;
      }
      try {
        await this.chats.removeMessage(this.id(), message.id, null);
        this.applyLocalDelete(message.id);
      } catch (error) {
        this.toasts.show(toProblem(error).message, 'error');
      }
      return;
    }
    this.editingId.set(null);
    this.reasonPromptId.set(message.id);
    this.reasonControl.setValue('');
    this.reasonError.set(null);
  }

  protected cancelReasonPrompt(): void {
    this.reasonPromptId.set(null);
  }

  protected async submitReasonDelete(message: ChatMessage): Promise<void> {
    if (this.reasonBusy()) {
      return;
    }
    const reason = this.reasonControl.value.trim();
    if (!reason) {
      this.reasonError.set('Укажите причину');
      return;
    }
    this.reasonBusy.set(true);
    this.reasonError.set(null);
    try {
      await this.chats.removeMessage(this.id(), message.id, reason);
      this.applyLocalDelete(message.id);
      this.reasonPromptId.set(null);
    } catch (error) {
      this.reasonError.set(toProblem(error).message);
    } finally {
      this.reasonBusy.set(false);
    }
  }

  private applyLocalDelete(messageId: string): void {
    this.messages.update((list) =>
      list.map((m) => (m.id === messageId ? { ...m, deleted: true, body: null, attachments: [] } : m)),
    );
  }

  /** Имена отправителей и собственная роль (для прав на чужое удаление) — необязательное удобство, без них чат остаётся читаемым. */
  private async loadMembers(conversationId: string): Promise<void> {
    try {
      const members = await this.groupChats.members(conversationId);
      if (conversationId !== this.id()) {
        return;
      }
      this.members.set(members);
    } catch {
      // имена отправителей и права модератора — необязательное удобство
    }
  }

  protected purposeLabel(purpose: string): string {
    return PURPOSE_LABELS[purpose] ?? 'Вложение';
  }

  protected attachmentKind(purpose: string): 'image' | 'document' {
    return purpose === 'CHAT_IMAGE' ? 'image' : 'document';
  }

  protected time(iso: string): string {
    return new Date(iso).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  }

  protected canAddAttachment(): boolean {
    return this.attachSlots().length < ATTACHMENTS_MAX;
  }

  protected addSlot(purpose: MediaPurpose): void {
    if (!this.canAddAttachment()) {
      return;
    }
    this.attachSlots.update((slots) => [...slots, { slotId: `slot-${Date.now()}-${Math.random()}`, purpose }]);
  }

  protected onSlotReady(slotId: string, media: UploadedMedia): void {
    this.attachReady.update((map) => ({ ...map, [slotId]: media }));
  }

  protected removeSlot(slotId: string): void {
    this.attachSlots.update((slots) => slots.filter((s) => s.slotId !== slotId));
    this.attachReady.update((map) => {
      const next = { ...map };
      delete next[slotId];
      return next;
    });
  }

  /** Текст или готовое вложение обязательны; незавершённая загрузка держит отправку заблокированной. */
  protected canSend(): boolean {
    const hasText = this.textControl.value.trim().length > 0;
    const slots = this.attachSlots();
    const ready = this.attachReady();
    const allReady = slots.every((s) => ready[s.slotId]);
    return (hasText || slots.length > 0) && allReady;
  }

  protected async send(): Promise<void> {
    if (!this.canSend()) {
      return;
    }
    const body = this.textControl.value.trim() || null;
    const ready = this.attachReady();
    const attachments: PendingAttachment[] = this.attachSlots()
      .map((s) => ready[s.slotId])
      .filter((m): m is UploadedMedia => !!m)
      .map((m) => ({ mediaId: m.id, purpose: m.purpose }));

    const outgoing: PendingMessage = {
      clientMessageId: crypto.randomUUID(),
      body,
      attachments,
      status: 'pending',
      errorText: null,
      createdAt: new Date().toISOString(),
    };
    this.pending.update((list) => [...list, outgoing]);
    this.textControl.setValue('');
    this.attachSlots.set([]);
    this.attachReady.set({});
    afterNextRender(() => this.scrollToEnd(), { injector: this.injector });
    this.dispatch(outgoing);
  }

  protected retry(clientMessageId: string): void {
    const item = this.pending().find((p) => p.clientMessageId === clientMessageId);
    if (!item || item.status === 'pending') {
      return;
    }
    this.pending.update((list) =>
      list.map((p) => (p.clientMessageId === clientMessageId ? { ...p, status: 'pending', errorText: null } : p)),
    );
    this.dispatch(item);
  }

  private dispatch(item: PendingMessage): void {
    const ok = this.realtime.publish(this.id(), 'messages', {
      clientMessageId: item.clientMessageId,
      body: item.body,
      attachments: item.attachments.map((a) => a.mediaId),
    });
    if (!ok) {
      this.markFailed(item.clientMessageId, 'Нет соединения. Повторите после восстановления связи.');
    }
  }

  private onRealtimeEvent(event: RealtimeEvent): void {
    if (event.conversationId !== this.id()) {
      return;
    }
    if (event.type === 'message.saved') {
      this.onAck(event.payload as AckPayload);
    } else if (event.type === 'message.failed') {
      const payload = event.payload as FailedPayload;
      this.markFailed(payload.clientMessageId, messageForCode(payload.code));
    } else if (event.type === 'message.created') {
      void this.syncIncoming();
    } else if (event.type === 'message.edited') {
      this.applyEdited(event.payload as EditedPayload);
    } else if (event.type === 'message.deleted') {
      this.applyDeleted(event.payload as DeletedPayload);
    }
  }

  /** Подтверждение переносит сообщение из pending в messages — второй пузырь не создаётся. */
  private onAck(payload: AckPayload): void {
    const item = this.pending().find((p) => p.clientMessageId === payload.clientMessageId);
    if (!item) {
      return;
    }
    this.pending.update((list) => list.filter((p) => p.clientMessageId !== payload.clientMessageId));
    if (this.messages().some((m) => m.id === payload.messageId)) {
      return;
    }
    const message: ChatMessage = {
      id: payload.messageId,
      seq: payload.seq,
      senderId: this.selfId() ?? '',
      body: item.body,
      createdAt: item.createdAt,
      updatedAt: item.createdAt,
      version: 0,
      deleted: false,
      attachments: item.attachments.map((a, i) => ({ mediaId: a.mediaId, position: i + 1, purpose: a.purpose })),
    };
    this.messages.update((list) => [...list, message]);
    afterNextRender(() => this.scrollToEnd(), { injector: this.injector });
  }

  /** Применяется только если версия события новее уже показанной — собственное REST-действие и его эхо не задваиваются. */
  private applyEdited(payload: EditedPayload): void {
    this.messages.update((list) =>
      list.map((m) =>
        m.id === payload.messageId && payload.version > m.version
          ? { ...m, body: payload.body, version: payload.version, updatedAt: payload.updatedAt }
          : m,
      ),
    );
  }

  private applyDeleted(payload: DeletedPayload): void {
    this.messages.update((list) =>
      list.map((m) =>
        m.id === payload.messageId && payload.version > m.version
          ? { ...m, deleted: true, body: null, attachments: [], version: payload.version, updatedAt: payload.updatedAt }
          : m,
      ),
    );
  }

  private markFailed(clientMessageId: string, text: string): void {
    this.pending.update((list) =>
      list.map((p) => (p.clientMessageId === clientMessageId ? { ...p, status: 'error', errorText: text } : p)),
    );
  }

  /** `message.created` несёт только идентификаторы — содержимое подтягиваем тем же REST, что и историю. */
  private async syncIncoming(): Promise<void> {
    const conversationId = this.id();
    try {
      const page = await this.chats.history(conversationId, null);
      if (conversationId !== this.id()) {
        return;
      }
      const seen = new Set(this.messages().map((m) => m.id));
      const fresh = oldestFirst(page.items).filter((m) => !seen.has(m.id));
      if (fresh.length > 0) {
        this.messages.update((list) => [...list, ...fresh]);
        afterNextRender(() => this.scrollToEnd(), { injector: this.injector });
      }
    } catch {
      // приход события — необязательное ускорение; обычная перезагрузка диалога тоже подхватит историю
    }
  }

  private scrollToEnd(): void {
    const scroller = this.scroller()?.nativeElement;
    if (scroller) {
      scroller.scrollTop = scroller.scrollHeight;
    }
  }
}

/** Сервер присылает новые сообщения первыми; на экране старые выше. */
function oldestFirst(items: ChatMessage[]): ChatMessage[] {
  return [...items].reverse();
}
