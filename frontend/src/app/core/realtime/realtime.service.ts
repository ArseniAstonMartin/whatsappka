import { Injectable, effect, inject, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';
import { AuthService } from '../auth.service';
import { ConnectionState } from '../connection-state';
import { EventDeduplicator } from './event-dedup';
import { reconnectDelayMs } from './reconnect-delay';

export interface RealtimeEvent {
  eventId: string;
  type: string;
  occurredAt: string;
  entityId: string | null;
  entityVersion: number | null;
  conversationId: string | null;
  eventSeq: number | null;
  payload: unknown;
}

export type ChatCommand = 'messages' | 'typing' | 'read';

/**
 * Общий STOMP-клиент. Подключается после входа, отключается при выходе, токен передаёт в CONNECT-заголовке.
 * Переподключение с джиттером; после восстановления потребители получают сигнал синхронизации через REST.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly auth = inject(AuthService);
  private readonly connection = inject(ConnectionState);

  private readonly eventsSubject = new Subject<RealtimeEvent>();
  private readonly resyncSubject = new Subject<void>();
  private readonly dedup = new EventDeduplicator();

  /** Новые события после отбрасывания повторов. */
  readonly events$: Observable<RealtimeEvent> = this.eventsSubject.asObservable();
  /** Сигнал «перезагрузите данные по REST»: после каждого переподключения. */
  readonly resync$: Observable<void> = this.resyncSubject.asObservable();
  readonly connected = signal(false);

  private client: Client | null = null;
  private subscriptions: StompSubscription[] = [];
  private attempt = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private hasConnectedBefore = false;
  private stopped = true;

  constructor() {
    effect(() => {
      const status = this.auth.status();
      if (status === 'authenticated') {
        this.start();
      } else if (status === 'guest') {
        this.stop();
      }
    });
  }

  /** Отправка команды чата. false — соединения нет: вызывающий код сохраняет введённый текст. */
  publish(conversationId: string, command: ChatCommand, body: unknown): boolean {
    if (!this.client?.connected) {
      return false;
    }
    this.client.publish({
      destination: `/app/conversations/${conversationId}/${command}`,
      body: JSON.stringify(body),
    });
    return true;
  }

  private start(): void {
    if (!this.stopped) {
      return;
    }
    this.stopped = false;
    this.attempt = 0;
    this.open();
  }

  private stop(): void {
    this.stopped = true;
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.subscriptions = [];
    void this.client?.deactivate();
    this.client = null;
    this.connected.set(false);
    this.connection.setStatus('online');
  }

  private open(): void {
    const token = this.auth.token();
    if (!token) {
      void this.reconnect();
      return;
    }
    const client = new Client({
      brokerURL: this.brokerUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      // Переподключение ведём сами: нужен джиттер и обновление токена перед попыткой.
      reconnectDelay: 0,
      onConnect: () => this.onConnected(client),
      onStompError: () => this.onDropped(),
      onWebSocketClose: () => this.onDropped(),
    });
    this.client = client;
    client.activate();
  }

  private onConnected(client: Client): void {
    if (client !== this.client || this.stopped) {
      return;
    }
    this.attempt = 0;
    this.subscriptions = [
      client.subscribe('/user/queue/events', (message) => this.onEvent(message)),
      client.subscribe('/user/queue/errors', () => undefined),
    ];
    this.connected.set(true);
    this.connection.setStatus('online');
    if (this.hasConnectedBefore) {
      this.resyncSubject.next();
    }
    this.hasConnectedBefore = true;
  }

  private onDropped(): void {
    if (this.stopped) {
      return;
    }
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.subscriptions = [];
    this.connected.set(false);
    this.connection.setStatus('reconnecting');
    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer !== null || this.stopped) {
      return;
    }
    const delay = reconnectDelayMs(this.attempt++);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      void this.reconnect();
    }, delay);
  }

  /** Перед новой попыткой обновляем access-токен: истёкший токен сервер не примет. */
  private async reconnect(): Promise<void> {
    if (this.stopped) {
      return;
    }
    const renewed = await this.auth.refresh();
    if (!renewed) {
      this.stop();
      return;
    }
    if (this.stopped) {
      return;
    }
    this.client = null;
    this.open();
  }

  private onEvent(message: IMessage): void {
    let event: RealtimeEvent;
    try {
      event = JSON.parse(message.body) as RealtimeEvent;
    } catch {
      return;
    }
    if (this.dedup.accept(event)) {
      this.eventsSubject.next(event);
    }
  }

  private brokerUrl(): string {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}/ws`;
  }
}
