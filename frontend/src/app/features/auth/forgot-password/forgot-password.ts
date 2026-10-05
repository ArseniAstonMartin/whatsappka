import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccountRecoveryService } from '../../../core/account-recovery.service';
import { ApiProblem, toProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { controlErrorText } from '../../../shared/ui/messages';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, AppButton, TextField],
  templateUrl: './forgot-password.html',
  styleUrl: '../auth-page.scss',
})
export class ForgotPassword {
  private readonly recovery = inject(AccountRecoveryService);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });
  protected readonly submitting = signal(false);
  protected readonly sent = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);

  protected errorFor(): string | null {
    return controlErrorText(this.form.controls.email);
  }

  protected async submit(): Promise<void> {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.problem.set(null);
    try {
      await this.recovery.requestPasswordReset(this.form.getRawValue().email);
      // Сообщение одно и то же, есть аккаунт с таким адресом или нет.
      this.sent.set(true);
    } catch (error) {
      this.problem.set(toProblem(error));
    } finally {
      this.submitting.set(false);
    }
  }
}
