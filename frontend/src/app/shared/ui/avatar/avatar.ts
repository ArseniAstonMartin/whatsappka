import { Component, computed, input } from '@angular/core';

export type AvatarSize = 32 | 40 | 56 | 96;

/** Аватар: изображение с alt, либо инициалы. Имя доступно screen reader в обоих случаях. */
@Component({
  selector: 'app-avatar',
  templateUrl: './avatar.html',
  styleUrl: './avatar.scss',
  host: {
    '[attr.role]': '"img"',
    '[attr.aria-label]': 'name()',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
    '[style.font-size.px]': 'size() / 2.6',
  },
})
export class Avatar {
  readonly name = input.required<string>();
  readonly src = input<string | null>(null);
  readonly size = input<AvatarSize>(40);

  protected readonly initials = computed(() => initialsOf(this.name()));
}

export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return '?';
  }
  const first = parts[0].charAt(0);
  const second = parts.length > 1 ? parts[parts.length - 1].charAt(0) : parts[0].charAt(1);
  return (first + (second ?? '')).toUpperCase();
}
