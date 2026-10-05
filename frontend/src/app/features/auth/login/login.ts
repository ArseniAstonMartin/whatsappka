import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { toProblem, ApiProblem } from '../../../core/api-error';
import { AppButton } from '../../../shared/ui/button/app-button';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { controlErrorText } from '../../../shared/ui/messages';
import { applyServerErrors, safeNext } from '../auth-form';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, AppButton, TextField],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
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
    if (this.auth.status() === 'authenticated') {
      void this.router.navigateByUrl(safeNext(this.route.snapshot.queryParamMap.get('next')));
    }
  }

  protected errorFor(name: 'email' | 'password'): string | null {
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
    const { email, password } = this.form.getRawValue();
    try {
      await this.auth.login(email, password);
      await this.router.navigateByUrl(safeNext(this.route.snapshot.queryParamMap.get('next')));
    } catch (error) {
      const problem = toProblem(error);
      this.problem.set(problem);
      applyServerErrors(this.form, problem);
    } finally {
      this.submitting.set(false);
    }
  }
}
