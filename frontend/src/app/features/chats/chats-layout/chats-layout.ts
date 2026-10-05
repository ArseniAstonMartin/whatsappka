import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ChatList } from '../chat-list/chat-list';

/**
 * Раздел сообщений: список и активный диалог рядом на широком экране. На узком экране открытый диалог
 * занимает всё место, список возвращается кнопкой «Все чаты».
 */
@Component({
  selector: 'app-chats-layout',
  imports: [RouterOutlet, ChatList],
  templateUrl: './chats-layout.html',
  styleUrl: './chats-layout.scss',
})
export class ChatsLayout {
  private readonly router = inject(Router);

  protected readonly hasChat = signal(isChatUrl(this.router.url));

  constructor() {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe((event) => this.hasChat.set(isChatUrl(event.urlAfterRedirects)));
  }
}

function isChatUrl(url: string): boolean {
  return /^\/chats\/[^/?#]+/.test(url);
}
