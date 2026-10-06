import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ChatMessage, ChatService, Conversation, ReadStatus } from '../../../core/chat.service';
import { mergeMessages } from '../../../core/message-sync';
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
import { openReport } from '../../../shared/report/report-button';
import { Dialog } from '@angular/cdk/dialog';
import { fromEvent, interval } from 'rxjs';

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
  /** Не ушло из-за разрыва связи: после восстановления отправляется повторно с тем же ID. */
  offline: boolean;
  lastAttemptAt: number | null;
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
 * История диалога — отправка, правка и удаление сообщений.
 * Сервер отдаёт страницы от новых к старым; здесь они выводятся снизу вверх.
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
interface TypingPayload {
  userId: string;
  expiresInSeconds: number;
}

const TYPING_INTERVAL_MS = 2000;
/** Статус прочтения обновляется, пока вкладка видима: сервер не присылает чужое прочтение отдельным событием. */
const READ_STATUS_INTERVAL_MS = 15000;
/** REST также страхует потерю Redis-сигналов при живом WebSocket. */
const PRESENCE_INTERVAL_MS = 20000;
const SYNC_POLL_INTERVAL_MS = 5000;
/** Не больше стольких страниц журнала за один проход: остальное догонит следующий проход. */
const EVENT_PAGES_MAX = 5;

@Component({
  selector: 'app-chat-dialog',
  imports: [
    // FormsModule нужен, чтобы (ngSubmit) перехватывал отправку формы; иначе браузер перезагружает страницу.
    FormsModule,
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
  private readonly dialog = inject(Dialog);
  private readonly groupChats = inject(GroupChatService);
  private readonly profiles = inject(ProfileService);
  private readonly realtime = inject(RealtimeService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly injector = inject(Injector);

  readonly id = input.required<string>();

  protected readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  protected readonly conversation = signal<Conversation | null>(null);
  /** Собеседник личного диалога сейчас в сети. */
  protected readonly online = signal(false);
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
  protected readonly syncWarning = signal<string | null>(null);

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
  private historyRevision = 0;
  private openedId: string | null = null;
  private destroyed = false;

  /** Кто сейчас печатает: id → таймер, который снимает индикатор по сроку из сигнала (сигнал об окончании не нужен). */
  private readonly typingTimers = new Map<string, ReturnType<typeof setTimeout>>();
  protected readonly typingIds = signal<string[]>([]);
  protected readonly typingLabel = computed<string | null>(() => {
    const ids = this.typingIds();
    if (ids.length === 0) {
      return null;
    }
    if (ids.length > 1) {
      return 'Несколько участников печатают…';
    }
    const name = this.memberNames()[ids[0]] ?? this.conversation()?.otherDisplayName ?? 'Кто-то';
    return `${name} печатает…`;
  });
  private lastTypingSignal = 0;

  /** Последнее прочтение, отправленное серверу в этом диалоге. Повторно меньший или равный seq не шлём. */
  private lastReadSent = 0;
  /** Граница журнала, до которой клиент уже применил события. Null — синхронизация ещё не начиналась. */
  private eventCursor: number | null = null;
  private membershipId: string | null = null;
  private syncRun: object | null = null;
  private syncRequested = false;
  private syncTimer: ReturnType<typeof setTimeout> | null = null;
  /** Последнее своё неудалённое сообщение: под ним показывается подтверждённое прочтение. */
  protected readonly lastOwnId = computed<string | null>(() => {
    const own = this.messages().filter((m) => this.isOwn(m) && !m.deleted);
    return own.at(-1)?.id ?? null;
  });
  protected readonly readStatus = signal<ReadStatus | null>(null);
  /** Подпись только по подтверждённому прочтению: доставку без подтверждения не показываем. */
  protected readonly readLabel = computed<string | null>(() => {
    const status = this.readStatus();
    const chat = this.conversation();
    if (!status || !chat || status.readBy === 0) {
      return null;
    }
    return chat.type === 'GROUP' ? `Прочитали: ${status.readBy} из ${status.eligible}` : 'Прочитано';
  });

  constructor() {
    effect(() => {
      const conversationId = this.id();
      untracked(() => void this.start(conversationId));
    });
    this.realtime.events$.pipe(takeUntilDestroyed()).subscribe((event) => this.onRealtimeEvent(event));
    this.textControl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.signalTyping());
    // Вернулась видимость вкладки: отмечаем прочитанным то, что пришло, пока её не было видно.
    fromEvent(document, 'visibilitychange').pipe(takeUntilDestroyed()).subscribe(() => {
      if (document.visibilityState === 'visible') {
        void this.syncEvents();
        this.markRead();
        void this.refreshReadStatus();
      }
    });
    effect(() => {
      if (!this.realtime.connected()) {
        untracked(() => this.connectionLost());
      }
    });
    this.realtime.resync$.pipe(takeUntilDestroyed()).subscribe(() => {
      this.lastReadSent = 0;
      void this.syncEvents();
    });
    interval(SYNC_POLL_INTERVAL_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        void this.syncEvents();
      });
    interval(PRESENCE_INTERVAL_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (document.visibilityState === 'visible') {
          void this.refreshPresence();
        }
      });
    interval(READ_STATUS_INTERVAL_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (document.visibilityState === 'visible') {
          void this.refreshReadStatus();
        }
      });
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      ++this.loadToken;
      this.clearTyping();
      if (this.syncTimer !== null) clearTimeout(this.syncTimer);
    });
  }

  private async refreshPresence(): Promise<void> {
    const chat = this.conversation();
    if (!chat || chat.type !== 'DIRECT' || !chat.otherId) {
      return;
    }
    try {
      const presence = await this.chats.presence(chat.id);
      this.online.set(presence.online.includes(chat.otherId));
    } catch {
      // Индикатор присутствия не обязателен: при ошибке просто не показываем «в сети».
      this.online.set(false);
    }
  }

  protected async start(conversationId: string): Promise<void> {
    const token = ++this.loadToken;
    ++this.historyRevision;
    this.syncRun = null;
    this.syncRequested = false;
    if (this.syncTimer !== null) clearTimeout(this.syncTimer);
    this.syncTimer = null;
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    this.syncWarning.set(null);
    this.messages.set([]);
    if (this.openedId !== conversationId) {
      this.pending.set([]);
      this.textControl.setValue('');
      this.attachSlots.set([]);
      this.attachReady.set({});
    }
    this.openedId = conversationId;
    this.conversation.set(null);
    this.online.set(false);
    this.members.set([]);
    this.loadingOlder.set(false);
    this.editingId.set(null);
    this.reasonPromptId.set(null);
    this.clearTyping();
    this.lastReadSent = 0;
    this.readStatus.set(null);
    this.eventCursor = null;
    this.membershipId = null;
    try {
      // Граница журнала — до истории: событие между ними попадёт в синхронизацию и применится дважды безопасно.
      const head = await this.chats.eventsHead(conversationId);
      if (!this.isCurrent(conversationId, token)) {
        return;
      }
      const [conversation, own, page] = await Promise.all([
        this.chats.get(conversationId),
        this.selfId() ? Promise.resolve(null) : this.profiles.own(),
        this.chats.history(conversationId, null),
      ]);
      if (!this.isCurrent(conversationId, token)) {
        return;
      }
      if (own) {
        this.selfId.set(own.id);
      }
      this.conversation.set(conversation);
      void this.refreshPresence();
      this.messages.set(oldestFirst(page.items));
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
      this.eventCursor = head.cursor;
      this.membershipId = head.membershipId;
      this.reconcilePending(page.items);
      this.markRead();
      void this.refreshReadStatus();
      if (conversation.type === 'GROUP') {
        void this.loadMembers(conversationId);
      }
      afterNextRender(() => this.scrollToEnd(), { injector: this.injector });
    } catch (error) {
      if (!this.isCurrent(conversationId, token)) {
        return;
      }
      const problem = toProblem(error);
      if (problem.status === 404 || problem.status === 403) {
        this.revokeAccess();
      } else {
        this.error.set(problem.message);
      }
    } finally {
      if (token === this.loadToken) {
        this.loading.set(false);
        if (this.eventCursor !== null) void this.syncEvents();
      }
    }
  }

  protected async loadOlder(): Promise<void> {
    const cursor = this.nextCursor();
    const conversationId = this.id();
    const token = this.loadToken;
    const revision = this.historyRevision;
    const scroller = this.scroller()?.nativeElement;
    if (this.loadingOlder() || !this.hasMore() || !cursor || !scroller) {
      return;
    }
    const heightBefore = scroller.scrollHeight;
    const topBefore = scroller.scrollTop;
    this.loadingOlder.set(true);
    try {
      const page = await this.chats.history(conversationId, cursor);
      if (!this.isCurrent(conversationId, token) || revision !== this.historyRevision) {
        return;
      }
      this.messages.update((list) => mergeMessages(list, page.items));
      this.reconcilePending(page.items);
      this.nextCursor.set(page.nextCursor);
      this.hasMore.set(page.hasMore);
      afterNextRender(
        () => {
          scroller.scrollTop = scroller.scrollHeight - heightBefore + topBefore;
        },
        { injector: this.injector },
      );
    } catch (error) {
      if (this.isCurrent(conversationId, token) && revision === this.historyRevision) {
        if ([403, 404].includes(toProblem(error).status)) this.revokeAccess();
        else this.toasts.show(toProblem(error).message, 'error');
      }
    } finally {
      if (this.isCurrent(conversationId, token) && revision === this.historyRevision) this.loadingOlder.set(false);
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
    if (!this.isOwn(message) && !message.deleted) {
      actions.push({ label: 'Пожаловаться', run: () => openReport(this.dialog, this.toasts, 'MESSAGE', message.id) });
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
    const token = this.loadToken;
    try {
      const members = await this.groupChats.members(conversationId);
      if (!this.isCurrent(conversationId, token) || this.notFound()) {
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
    return !this.loading() && !this.notFound() && !this.conversation()?.blocked && this.textControl.valid
      && (hasText || slots.length > 0) && allReady;
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
      offline: false,
      lastAttemptAt: null,
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
    if (!item || item.status === 'pending' || this.notFound() || this.loading() || this.conversation()?.blocked) {
      return;
    }
    this.pending.update((list) =>
      list.map((p) => (p.clientMessageId === clientMessageId ? { ...p, status: 'pending', errorText: null, offline: false } : p)),
    );
    this.dispatch(item);
  }

  private dispatch(item: PendingMessage): void {
    this.pending.update((list) => list.map((p) => p.clientMessageId === item.clientMessageId
      ? { ...p, lastAttemptAt: Date.now() } : p));
    const ok = this.realtime.publish(this.id(), 'messages', {
      clientMessageId: item.clientMessageId,
      body: item.body,
      attachments: item.attachments.map((a) => a.mediaId),
    });
    if (!ok) {
      this.markFailed(item.clientMessageId, 'Нет соединения. Повторите после восстановления связи.', true);
    }
  }

  private onRealtimeEvent(event: RealtimeEvent): void {
    if (event.conversationId !== this.id() || this.loading() || this.notFound() || this.destroyed) {
      return;
    }
    if (event.type === 'message.saved') {
      this.onAck(event.payload as AckPayload);
    } else if (event.type === 'message.failed') {
      const payload = event.payload as FailedPayload;
      this.markFailed(payload.clientMessageId, messageForCode(payload.code));
    } else if (event.type === 'message.created') {
      void this.syncEvents();
    } else if (event.type === 'message.edited') {
      this.applyEdited(event.payload as EditedPayload);
    } else if (event.type === 'message.deleted') {
      this.applyDeleted(event.payload as DeletedPayload);
    } else if (event.type === 'conversation.membership.changed') {
      void this.syncEvents();
    } else if (event.type === 'typing.changed') {
      this.onTyping(event.payload as TypingPayload);
    }
  }

  /** Сигнал набора обновляет срок индикатора; по его истечении собеседник перестаёт печатать. */
  private onTyping(payload: TypingPayload): void {
    if (payload.userId === this.selfId()) {
      return;
    }
    this.stopTyping(payload.userId, false);
    const seconds = Math.min(Math.max(payload.expiresInSeconds, 1), 10);
    this.typingTimers.set(payload.userId, setTimeout(() => this.stopTyping(payload.userId), seconds * 1000));
    this.typingIds.update((ids) => (ids.includes(payload.userId) ? ids : [...ids, payload.userId]));
  }

  private stopTyping(userId: string, update = true): void {
    const timer = this.typingTimers.get(userId);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.typingTimers.delete(userId);
    }
    if (update) {
      this.typingIds.update((ids) => ids.filter((id) => id !== userId));
    }
  }

  private clearTyping(): void {
    for (const timer of this.typingTimers.values()) {
      clearTimeout(timer);
    }
    this.typingTimers.clear();
    this.typingIds.set([]);
  }

  /** Прочтение только для открытого и видимого диалога: фоновая вкладка историю не отмечает. */
  private markRead(): void {
    if (document.visibilityState !== 'visible' || this.notFound() || this.destroyed) {
      return;
    }
    const latest = this.messages().reduce((max, m) => Math.max(max, m.seq), 0);
    if (latest <= this.lastReadSent) {
      return;
    }
    if (this.realtime.publish(this.id(), 'read', { seq: latest })) {
      this.lastReadSent = latest;
    }
  }

  private async refreshReadStatus(): Promise<void> {
    const conversationId = this.id();
    const token = this.loadToken;
    const messageId = this.lastOwnId();
    if (!messageId) {
      this.readStatus.set(null);
      return;
    }
    try {
      const status = await this.chats.readStatus(conversationId, messageId);
      if (this.isCurrent(conversationId, token) && messageId === this.lastOwnId()) {
        this.readStatus.set(status);
      }
    } catch {
      // Подпись прочтения — подсказка: при ошибке она просто не показывается, диалог работает.
      if (this.isCurrent(conversationId, token)) this.readStatus.set(null);
    }
  }

  /** Сигнал набора не чаще раза в 2 секунды, как и на сервере. Без соединения набор просто не виден собеседнику. */
  private signalTyping(): void {
    if (this.textControl.value.trim().length === 0) {
      return;
    }
    const now = Date.now();
    if (now - this.lastTypingSignal < TYPING_INTERVAL_MS) {
      return;
    }
    if (this.realtime.publish(this.id(), 'typing', {})) {
      this.lastTypingSignal = now;
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
      version: -1,
      clientMessageId: item.clientMessageId,
      deleted: false,
      attachments: item.attachments.map((a, i) => ({ mediaId: a.mediaId, position: i + 1, purpose: a.purpose })),
    };
    this.messages.update((list) => mergeMessages(list, [message]));
    void this.syncEvents();
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

  private markFailed(clientMessageId: string, text: string, offline = false): void {
    this.pending.update((list) =>
      list.map((p) => (p.clientMessageId === clientMessageId ? { ...p, status: 'error', errorText: text, offline } : p)),
    );
  }

  private connectionLost(): void {
    this.lastReadSent = 0;
    this.clearTyping();
    this.pending.update((list) => list.map((item) => item.status === 'pending'
      ? { ...item, status: 'error', offline: true, errorText: 'Нет подтверждения. Повторим после восстановления связи.' }
      : item));
  }

  /** История подтверждает сохранение даже тогда, когда ACK не дошёл. */
  private reconcilePending(messages: ChatMessage[]): void {
    const saved = new Set(messages.filter((m) => m.senderId === this.selfId()).map((m) => m.clientMessageId));
    this.pending.update((list) => list.filter((p) => !saved.has(p.clientMessageId)));
  }

  private retryOffline(): void {
    if (!this.realtime.connected() || this.conversation()?.blocked || this.notFound()) return;
    for (const item of this.pending()) {
      if (item.status === 'pending' && item.lastAttemptAt !== null && Date.now() - item.lastAttemptAt >= 10000) {
        this.markFailed(item.clientMessageId, 'Подтверждение задерживается. Повторяем отправку.', true);
      }
    }
    for (const item of this.pending()) {
      if (item.status === 'error' && item.offline) this.retry(item.clientMessageId);
    }
  }

  private isCurrent(conversationId: string, token: number): boolean {
    return !this.destroyed && token === this.loadToken && conversationId === this.id();
  }

  private revokeAccess(): void {
    ++this.loadToken;
    ++this.historyRevision;
    this.eventCursor = null;
    this.membershipId = null;
    this.syncRun = null;
    this.syncRequested = false;
    this.notFound.set(true);
    this.loading.set(false);
    this.loadingOlder.set(false);
    this.messages.set([]);
    this.pending.set([]);
    this.members.set([]);
    this.conversation.set(null);
    this.online.set(false);
    this.readStatus.set(null);
    this.editingId.set(null);
    this.reasonPromptId.set(null);
    this.textControl.setValue('');
    this.attachReady.set({});
    this.attachSlots.set([]);
    this.clearTyping();
  }

  /** Применяем снимки каждой страницы до продвижения курсора, а не только последние 50 сообщений. */
  private async syncEvents(): Promise<void> {
    const conversationId = this.id();
    const token = this.loadToken;
    if (this.destroyed || this.eventCursor === null || this.loading() || this.notFound()) return;
    if (this.syncRun !== null) {
      this.syncRequested = true;
      return;
    }
    const run = {};
    this.syncRun = run;
    this.syncRequested = false;
    let succeeded = false;
    try {
      const conversation = await this.chats.get(conversationId);
      if (!this.isCurrent(conversationId, token)) return;
      this.conversation.set(conversation);
      void this.refreshPresence();
      for (let page = 0; page < EVENT_PAGES_MAX; page++) {
        const events = await this.chats.events(conversationId, this.eventCursor!);
        if (!this.isCurrent(conversationId, token)) return;
        if (events.fullSyncRequired || events.membershipId !== this.membershipId) {
          await this.reloadFull(conversationId, token);
          if (!this.isCurrent(conversationId, token)) return;
          continue;
        }
        // Ответ старой страницы, запрошенный до этих изменений, уже может быть устаревшим.
        if (events.messages.length > 0 && this.loadingOlder()) {
          ++this.historyRevision;
          this.loadingOlder.set(false);
        }
        const current = this.messages();
        const oldest = current.length ? current[0].seq : 0;
        // Правка ещё не загруженной старой страницы не создаёт разрыв в показанной истории.
        const visible = events.messages.filter((m) => !this.hasMore() || m.seq >= oldest);
        this.messages.update((list) => mergeMessages(list, visible));
        this.reconcilePending(events.messages);
        this.eventCursor = events.nextCursor ?? this.eventCursor;
        if (events.items.some((event) => event.messageId === null)) {
          void this.loadMembers(conversationId);
        }
        if (!events.hasMore) break;
      }
      if (!this.isCurrent(conversationId, token)) return;
      this.syncWarning.set(null);
      this.retryOffline();
      this.markRead();
      void this.refreshReadStatus();
      succeeded = true;
    } catch (error) {
      if (this.isCurrent(conversationId, token)) {
        if ([403, 404].includes(toProblem(error).status)) this.revokeAccess();
        else this.syncWarning.set('Не удалось обновить сообщения. Повторяем подключение…');
      }
    } finally {
      if (this.syncRun === run) {
        this.syncRun = null;
        if (succeeded && this.syncRequested && this.isCurrent(conversationId, token)) {
          this.syncTimer = setTimeout(() => {
            this.syncTimer = null;
            if (this.isCurrent(conversationId, token)) void this.syncEvents();
          }, 100);
        }
      }
    }
  }

  /** Новый снимок истории после истечения журнала; pending не теряется при обычной пересинхронизации. */
  private async reloadFull(conversationId: string, token: number): Promise<void> {
    const revision = ++this.historyRevision;
    this.loadingOlder.set(false);
    const head = await this.chats.eventsHead(conversationId);
    if (!this.isCurrent(conversationId, token)) return;
    if (head.membershipId !== this.membershipId) {
      this.messages.set([]);
      this.pending.set([]);
      this.members.set([]);
      this.editingId.set(null);
      this.reasonPromptId.set(null);
      this.lastReadSent = 0;
    }
    const page = await this.chats.history(conversationId, null);
    if (!this.isCurrent(conversationId, token) || revision !== this.historyRevision) return;
    const pageIds = new Set(page.items.map((message) => message.id));
    const live = this.messages().filter((message) => pageIds.has(message.id));
    this.messages.set(mergeMessages(page.items, live));
    this.reconcilePending(page.items);
    this.nextCursor.set(page.nextCursor);
    this.hasMore.set(page.hasMore);
    this.eventCursor = head.cursor;
    this.membershipId = head.membershipId;
    if (this.conversation()?.type === 'GROUP') void this.loadMembers(conversationId);
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
