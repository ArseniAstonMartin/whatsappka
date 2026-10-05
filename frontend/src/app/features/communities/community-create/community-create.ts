import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommunityService } from '../../../core/community.service';
import { toProblem, ApiProblem } from '../../../core/api-error';
import { applyServerErrors } from '../../auth/auth-form';
import { controlErrorText } from '../../../shared/ui/messages';
import { AppButton } from '../../../shared/ui/button/app-button';
import { Card } from '../../../shared/ui/card/card';
import { TextField } from '../../../shared/ui/text-field/text-field';

const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]*[a-z0-9]$/;

/** Форма создания сообщества: адрес, название, описание и видимость. */
@Component({
  selector: 'app-community-create',
  imports: [ReactiveFormsModule, RouterLink, AppButton, Card, TextField],
  templateUrl: './community-create.html',
  styleUrl: './community-create.scss',
})
export class CommunityCreate {
  private readonly communities = inject(CommunityService);
  private readonly router = inject(Router);

  protected readonly saving = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly formMessage = computed(() => this.problem()?.message ?? null);

  protected readonly form = new FormGroup({
    slug: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3), Validators.maxLength(60), Validators.pattern(SLUG_PATTERN)],
    }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(2000)] }),
    visibility: new FormControl<'PUBLIC' | 'PRIVATE'>('PUBLIC', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    for (const control of Object.values(this.form.controls) as AbstractControl[]) {
      control.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        if (control.hasError('server')) {
          const { server: _ignored, ...rest } = control.errors ?? {};
          control.setErrors(Object.keys(rest).length ? rest : null);
        }
      });
    }
  }

  protected errorFor(name: 'slug' | 'name' | 'description'): string | null {
    return controlErrorText(this.form.controls[name]);
  }

  protected descriptionLength(): number {
    return this.form.controls.description.value.length;
  }

  protected async submit(): Promise<void> {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.problem.set(null);
    try {
      await this.communities.create({
        slug: value.slug.trim(),
        name: value.name.trim(),
        description: value.description,
        visibility: value.visibility,
      });
      await this.router.navigate(['/groups', value.slug.trim()]);
    } catch (error) {
      const problem = toProblem(error);
      this.problem.set(problem);
      applyServerErrors(this.form, problem);
    } finally {
      this.saving.set(false);
    }
  }
}
