import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GroupChatService } from '../../../core/group-chat.service';
import { toProblem, ApiProblem } from '../../../core/api-error';
import { controlErrorText } from '../../../shared/ui/messages';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Card } from '../../../shared/ui/card/card';
import { TextField } from '../../../shared/ui/text-field/text-field';

/** Создание группового чата: только название, аватар добавляется позже в настройках (TASK-062). */
@Component({
  selector: 'app-group-chat-create',
  imports: [ReactiveFormsModule, RouterLink, AppButton, Card, TextField],
  templateUrl: './group-chat-create.html',
  styleUrl: './group-chat-create.scss',
})
export class GroupChatCreate {
  private readonly groups = inject(GroupChatService);
  private readonly router = inject(Router);

  protected readonly saving = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly formMessage = computed(() => this.problem()?.message ?? null);

  protected readonly form = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
  });

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
      const ref = await this.groups.create(this.form.controls.title.value.trim());
      await this.router.navigate(['/chats', ref.id]);
    } catch (error) {
      this.problem.set(toProblem(error));
    } finally {
      this.saving.set(false);
    }
  }
}
