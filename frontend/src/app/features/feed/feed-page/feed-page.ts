import { Component, inject } from '@angular/core';
import { CursorPage } from '../../../core/profile.service';
import { PostService, PostSummaryPublic } from '../../../core/post.service';
import { PostList } from '../../../shared/post-list/post-list';

/** Лента подписок: свои посты, подписки и группы (TASK-045, TASK-047). */
@Component({
  selector: 'app-feed-page',
  imports: [PostList],
  templateUrl: './feed-page.html',
  styleUrl: './feed-page.scss',
})
export class FeedPage {
  private readonly posts = inject(PostService);

  /** Стабильная ссылка на метод: пересоздание на каждый рендер заставило бы список перезагружаться. */
  protected readonly loadFeed = (cursor: string | null): Promise<CursorPage<PostSummaryPublic>> => this.posts.feed(cursor);
}
