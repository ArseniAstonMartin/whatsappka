import { Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let nextId = 0;

/** Поле ввода с подписью и текстом ошибки. Работает с Reactive Forms через ControlValueAccessor. */
@Component({
  selector: 'app-text-field',
  templateUrl: './text-field.html',
  styleUrl: './text-field.scss',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TextField), multi: true }],
})
export class TextField implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly type = input<'text' | 'email' | 'password'>('text');
  readonly autocomplete = input<string>('off');
  readonly hint = input<string>('');
  /** Многострочное поле для описания; по умолчанию однострочное. */
  readonly multiline = input<boolean>(false);
  readonly maxLength = input<number | null>(null);
  readonly errorText = input<string | null>(null);

  protected readonly inputId = `field-${nextId++}`;
  protected readonly value = signal('');
  protected readonly disabled = signal(false);

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected onInput(event: Event): void {
    const next = (event.target as HTMLInputElement).value;
    this.value.set(next);
    this.onChange(next);
  }

  protected onBlur(): void {
    this.onTouched();
  }

  /** Связывает поле с текстом ошибки или подсказки для screen reader. */
  protected describedBy(): string | null {
    if (this.errorText()) {
      return `${this.inputId}-error`;
    }
    return this.hint() ? `${this.inputId}-hint` : null;
  }
}
