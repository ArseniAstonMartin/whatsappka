import { Component, inject, input } from '@angular/core';
import { SearchService } from '../../../core/search.service';
import { PostList } from '../../../shared/post-list/post-list';

/** Лента хештега: только видимые зрителю публикации, по тем же правилам, что и поиск. */
@Component({
  selector: 'app-hashtag-page',
  imports: [PostList],
  templateUrl: './hashtag-page.html',
})
export class HashtagPage {
  private readonly search = inject(SearchService);

  readonly tag = input.required<string>();

  protected readonly loadPosts = (cursor: string | null) => this.search.hashtagPosts(this.tag(), cursor);
}
