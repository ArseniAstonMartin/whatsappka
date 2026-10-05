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
import { RouterLink } from '@angular/router';
import { ChatMessage, ChatService, Conversation } from '../../../core/chat.service';
import { ProfileService } from '../../../core/profile.service';
import { toProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Avatar } from '../../../shared/ui/avatar/avatar';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';
import { StatePanel } from '../../../shared/state-panel/state-panel';

const PURPOSE_LABELS: Record<string, string> = {
  CHAT_IMAGE: 'Изображение',
  CHAT_DOCUMENT: 'Документ',
};

/**
 * История личного диалога. Сервер отдаёт страницы от новых к старым; здесь они выводятся снизу вверх.
 * Подгрузка более старых страниц сохраняет место просмотра: высота, добавленная сверху, компенсируется прокруткой.
 */
@Component({
  selector: 'app-chat-dialog',
  imports: [AppButton, Avatar, Skeleton, StatePanel, RouterLink],
  templateUrl: './chat-dialog.html',
  styleUrl: './chat-dialog.scss',
})
export class ChatDialog {
  private readonly chats = inject(ChatService);
  private readonly profiles = inject(ProfileService);
  private readonly injector = inject(Injector);

  readonly id = input.required<string>();

  protected readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  protected readonly conversation = signal<Conversation | null>(null);
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly nextCursor = signal<string | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly selfId = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadingOlder = signal(false);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);

  private loadToken = 0;

  constructor() {
    effect(() => {
      const conversationId = this.id();
      untracked(() => void this.start(conversationId));
    });
  }

  protected async start(conversationId: string): Promise<void> {
    const token = ++this.loadToken;
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    this.messages.set([]);
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

  protected time(iso: string): string {
    return new Date(iso).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
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
