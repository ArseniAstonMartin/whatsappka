import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GroupChatService } from '../../../core/group-chat.service';
import { toProblem, ApiProblem } from '../../../core/api-error';
import { controlErrorText } from '../../../shared/ui/messages';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Card } from '../../../shared/ui/card/card';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { MediaUpload, UploadedMedia } from '../../../shared/media/media-upload/media-upload';

/** Создание группового чата: название и необязательная картинка; позже её меняют в настройках чата. */
@Component({
  selector: 'app-group-chat-create',
  imports: [ReactiveFormsModule, RouterLink, AppButton, Card, TextField, MediaUpload],
  templateUrl: './group-chat-create.html',
  styleUrl: './group-chat-create.scss',
})
export class GroupChatCreate {
  private readonly groups = inject(GroupChatService);
  private readonly router = inject(Router);

  protected readonly saving = signal(false);
  protected readonly avatarMediaId = signal<string | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly formMessage = computed(() => this.problem()?.message ?? null);

  protected readonly form = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
  });

  protected onAvatar(media: UploadedMedia): void {
    this.avatarMediaId.set(media.id);
  }

  protected errorFor(): string | null {
    return controlErrorText(this.form.controls.title);
  }

  protected async submit(): Promise<void> {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.problem.set(null);
    try {
      const ref = await this.groups.create(this.form.controls.title.value.trim(), this.avatarMediaId());
      await this.router.navigate(['/chats', ref.id]);
    } catch (error) {
      this.problem.set(toProblem(error));
    } finally {
      this.saving.set(false);
    }
  }
}
