import { Injectable, signal } from '@angular/core';

export type ConnectionStatus = 'online' | 'reconnecting' | 'offline';

/** Состояние realtime-соединения для баннера оболочки. Значение выставляет клиент STOMP. */
@Injectable({ providedIn: 'root' })
export class ConnectionState {
  private readonly statusState = signal<ConnectionStatus>('online');

  readonly status = this.statusState.asReadonly();

  setStatus(status: ConnectionStatus): void {
    this.statusState.set(status);
  }
}
