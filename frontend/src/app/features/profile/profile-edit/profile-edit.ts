import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ProfileService, OwnProfile, ProfilePatch } from '../../../core/profile.service';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { toProblem, ApiProblem } from '../../../core/api-error';
import { controlErrorText } from '../../../shared/ui/messages';
import { AppButton } from '../../../shared/ui/button/app-button';
import { TextField } from '../../../shared/ui/text-field/text-field';
import { applyServerErrors } from '../../auth/auth-form';

const TIMEZONES: string[] = (Intl as unknown as { supportedValuesOf?: (key: string) => string[] })
  .supportedValuesOf?.('timeZone') ?? ['UTC'];

/** Редактирование текстовых полей. Сохраняет только изменённые поля; при ошибке ввод остаётся на месте. */
@Component({
  selector: 'app-profile-edit',
  imports: [ReactiveFormsModule, AppButton, TextField],
  templateUrl: './profile-edit.html',
  styleUrl: './profile-edit.scss',
})
export class ProfileEdit implements OnInit {
  private readonly profiles = inject(ProfileService);
  private readonly toasts = inject(ToastService);

  readonly profile = input.required<OwnProfile>();
  readonly saved = output<OwnProfile>();

  protected readonly timezones = TIMEZONES;
  protected readonly saving = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly formMessage = computed(() => this.problem()?.message ?? null);

  protected readonly form = new FormGroup({
    displayName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    bio: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
    statusText: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(140)] }),
    timezone: new FormControl('UTC', { nonNullable: true, validators: [Validators.required] }),
  });

  private baseline: ProfilePatch = {};

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

  ngOnInit(): void {
    this.reset(this.profile());
  }

  protected errorFor(name: 'displayName' | 'bio' | 'statusText'): string | null {
    return controlErrorText(this.form.controls[name]);
  }

  protected length(name: 'bio' | 'statusText'): number {
    return this.form.controls[name].value.length;
  }

  protected async submit(): Promise<void> {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const patch = this.changes();
    if (Object.keys(patch).length === 0) {
      this.toasts.show('Изменений нет');
      return;
    }
    this.saving.set(true);
    this.problem.set(null);
    try {
      const saved = await this.profiles.patch(patch);
      this.reset(saved);
      this.toasts.show('Профиль сохранён', 'success');
      this.saved.emit(saved);
    } catch (error) {
      const problem = toProblem(error);
      this.problem.set(problem);
      applyServerErrors(this.form, problem);
    } finally {
      this.saving.set(false);
    }
  }

  private changes(): ProfilePatch {
    const value = this.form.getRawValue();
    const patch: ProfilePatch = {};
    if (value.displayName.trim() !== (this.baseline.displayName ?? '')) patch.displayName = value.displayName.trim();
    if (value.bio !== (this.baseline.bio ?? '')) patch.bio = value.bio;
    if (value.statusText !== (this.baseline.statusText ?? '')) patch.statusText = value.statusText;
    if (value.timezone !== (this.baseline.timezone ?? '')) patch.timezone = value.timezone;
    return patch;
  }

  private reset(profile: OwnProfile): void {
    this.baseline = {
      displayName: profile.displayName,
      bio: profile.bio,
      statusText: profile.statusText,
      timezone: profile.timezone,
    };
    this.form.reset({
      displayName: profile.displayName,
      bio: profile.bio,
      statusText: profile.statusText,
      timezone: profile.timezone,
    });
  }
}
