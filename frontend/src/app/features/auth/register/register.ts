import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { ApiProblem, toProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { controlErrorText } from '../../../shared/ui/messages';
import { applyServerErrors } from '../auth-form';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, AppButton, TextField],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    username: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3), Validators.maxLength(30), Validators.pattern(/^[A-Za-z0-9_]+$/)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(10), Validators.maxLength(72)],
    }),
  });
  protected readonly submitting = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly formMessage = computed(() => this.problem()?.message ?? null);

  constructor() {
    for (const control of Object.values(this.form.controls)) {
      control.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        if (control.hasError('server')) {
          const { server: _ignored, ...rest } = control.errors ?? {};
          control.setErrors(Object.keys(rest).length ? rest : null);
        }
      });
    }
  }

  protected errorFor(name: 'email' | 'username' | 'password'): string | null {
    return controlErrorText(this.form.controls[name]);
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
    const { email, username, password } = this.form.getRawValue();
    try {
      await this.auth.register(email, username, password);
      await this.router.navigateByUrl('/feed');
    } catch (error) {
      const problem = toProblem(error);
      this.problem.set(problem);
      applyServerErrors(this.form, problem);
    } finally {
      this.submitting.set(false);
    }
  }
}
