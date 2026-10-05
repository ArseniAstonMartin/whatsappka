import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccountRecoveryService } from '../../../core/account-recovery.service';
import { ApiProblem, toProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { controlErrorText } from '../../../shared/ui/messages';

/** Совпадение паролей проверяется здесь: на сервер уходит один пароль. */
function samePassword(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const repeat = group.get('repeat')?.value;
  return password && repeat && password !== repeat ? { mismatch: true } : null;
}

@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, AppButton, TextField],
  templateUrl: './reset-password.html',
  styleUrl: '../auth-page.scss',
})
export class ResetPassword {
  private readonly recovery = inject(AccountRecoveryService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Токен берётся из адреса один раз и сразу убирается из адресной строки, чтобы не остаться в истории. */
  private readonly token = this.route.snapshot.queryParamMap.get('token');

  protected readonly form = new FormGroup(
    {
      password: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(10), Validators.maxLength(72)],
      }),
      repeat: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: samePassword },
  );
  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly noToken = !this.token;
  protected readonly expired = computed(() => this.problem()?.code === 'invalid_token');

  constructor() {
    if (this.token) {
      void this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    }
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.problem.set(null));
  }

  protected passwordError(): string | null {
    return controlErrorText(this.form.controls.password);
  }

  protected repeatError(): string | null {
    if (this.form.hasError('mismatch') && this.form.controls.repeat.dirty) {
      return 'Пароли не совпадают';
    }
    return controlErrorText(this.form.controls.repeat);
  }

  protected async submit(): Promise<void> {
    if (this.submitting() || !this.token) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.problem.set(null);
    try {
      await this.recovery.confirmPasswordReset(this.token, this.form.getRawValue().password);
      this.done.set(true);
    } catch (error) {
      this.problem.set(toProblem(error));
    } finally {
      this.submitting.set(false);
    }
  }
}
