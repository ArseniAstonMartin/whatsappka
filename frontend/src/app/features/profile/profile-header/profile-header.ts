import { Component, input } from '@angular/core';
import { MediaView } from '../../../shared/media/media-view/media-view';
import { Avatar } from '../../../shared/ui/avatar/avatar';
import { VerifiedBadge } from '../../../shared/ui/verified-badge/verified-badge';

/** Шапка профиля: обложка, аватар, имя с василёком, статус и описание. Действия передаёт родитель. */
@Component({
  selector: 'app-profile-header',
  imports: [MediaView, Avatar, VerifiedBadge],
  templateUrl: './profile-header.html',
  styleUrl: './profile-header.scss',
})
export class ProfileHeader {
  readonly displayName = input.required<string>();
  readonly username = input.required<string>();
  readonly statusText = input<string>('');
  readonly bio = input<string>('');
  readonly verified = input<boolean>(false);
  readonly avatarMediaId = input<string | null>(null);
  readonly coverMediaId = input<string | null>(null);
}
