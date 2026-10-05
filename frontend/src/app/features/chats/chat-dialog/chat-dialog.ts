import {
  Component,
  ElementRef,
  Injector,
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
import { ProfileService } from '../../../core/profile.service';
import { RealtimeEvent, RealtimeService } from '../../../core/realtime/realtime.service';
import { toProblem, messageForCode } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Avatar } from '../../../shared/ui/avatar/avatar';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { MediaUpload, UploadedMedia } from '../../../shared/media/media-upload/media-upload';
import { MediaView } from '../../../shared/media/media-view/media-view';
import { MediaPurpose } from '../../../shared/media/purposes';

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

/**
 * История личного диалога и отправка сообщений (TASK-061). Сервер отдаёт страницы от новых к старым;
 * здесь они выводятся снизу вверх. Подгрузка более старых страниц сохраняет место просмотра: высота,
 * добавленная сверху, компенсируется прокруткой.
 *
 * <p>Отправленное сообщение сперва живёт в {@link pending} с локальным client_message_id; подтверждение
 * (`message.saved`) переносит его в {@link messages} как единственный пузырь — второй не появляется.
 * Входящее `message.created` от другого участника только сигнализирует и не несёт содержимого, поэтому
 * список догружается тем же REST-запросом, что и история.
 */
@Component({
  selector: 'app-chat-dialog',
  imports: [ReactiveFormsModule, AppButton, Avatar, Skeleton, StatePanel, RouterLink, TextField, MediaUpload, MediaView],
  templateUrl: './chat-dialog.html',
  styleUrl: './chat-dialog.scss',
})
export class ChatDialog {
  private readonly chats = inject(ChatService);
  private readonly profiles = inject(ProfileService);
  private readonly realtime = inject(RealtimeService);
  private readonly injector = inject(Injector);

  readonly id = input.required<string>();

  protected readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  protected readonly conversation = signal<Conversation | null>(null);
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly pending = signal<PendingMessage[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly selfId = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadingOlder = signal(false);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly textControl = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(4000)] });
  protected readonly attachSlots = signal<AttachSlot[]>([]);
  protected readonly attachReady = signal<Record<string, UploadedMedia>>({});
  protected readonly attachmentsMax = ATTACHMENTS_MAX;

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
      deleted: false,
      attachments: item.attachments.map((a, i) => ({ mediaId: a.mediaId, position: i + 1, purpose: a.purpose })),
    };
    this.messages.update((list) => [...list, message]);
    afterNextRender(() => this.scrollToEnd(), { injector: this.injector });
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
