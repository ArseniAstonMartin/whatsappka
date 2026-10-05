import { AbstractControl } from '@angular/forms';

/** Русские сообщения для типовых ошибок форм. Строки сервера переводятся по коду, а не выводятся как есть. */
export function controlErrorText(control: AbstractControl | null): string | null {
  if (!control || !control.errors || !(control.touched || control.dirty)) {
    return null;
  }
  const errors = control.errors;
  if (typeof errors['server'] === 'string') {
    return errors['server'];
  }
  if (errors['required']) {
    return 'Обязательное поле';
  }
  if (errors['email']) {
    return 'Введите корректный адрес почты';
  }
  if (errors['minlength']) {
    return `Минимум ${errors['minlength'].requiredLength} символов`;
  }
  if (errors['maxlength']) {
    return `Не больше ${errors['maxlength'].requiredLength} символов`;
  }
  if (errors['pattern']) {
    return 'Некорректный формат';
  }
  return 'Проверьте значение';
}
