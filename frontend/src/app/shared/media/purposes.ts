const MIB = 1024 * 1024;

export type MediaPurpose = 'AVATAR' | 'COVER' | 'POST_IMAGE' | 'CHAT_IMAGE' | 'CHAT_DOCUMENT';

export interface PurposeRules {
  label: string;
  /** Текст для пользователя: форматы и предел. Сервер проверяет всё равно. */
  allowedText: string;
  accept: string;
  maxBytes: number;
  kind: 'image' | 'document';
}

const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/** Правила назначений по FR-10. Клиентская проверка только для подсказки, решение принимает сервер. */
export const PURPOSE_RULES: Record<MediaPurpose, PurposeRules> = {
  AVATAR: { label: 'Аватар', allowedText: 'JPEG, PNG или WebP до 5 МиБ', accept: IMAGE_TYPES.join(','), maxBytes: 5 * MIB, kind: 'image' },
  COVER: { label: 'Обложка', allowedText: 'JPEG, PNG или WebP до 5 МиБ', accept: IMAGE_TYPES.join(','), maxBytes: 5 * MIB, kind: 'image' },
  POST_IMAGE: { label: 'Изображение записи', allowedText: 'JPEG, PNG или WebP до 10 МиБ', accept: IMAGE_TYPES.join(','), maxBytes: 10 * MIB, kind: 'image' },
  CHAT_IMAGE: { label: 'Изображение в чате', allowedText: 'JPEG, PNG или WebP до 10 МиБ', accept: IMAGE_TYPES.join(','), maxBytes: 10 * MIB, kind: 'image' },
  CHAT_DOCUMENT: { label: 'Документ в чате', allowedText: 'PDF или TXT до 20 МиБ', accept: 'application/pdf,text/plain', maxBytes: 20 * MIB, kind: 'document' },
};

/** Ошибки, которые показываем текстом по коду статуса обработки. */
export const FAILURE_TEXT: Record<string, string> = {
  decode_failed: 'Файл не удалось прочитать как изображение',
  image_too_large: 'Изображение больше 25 мегапикселей',
  processing_failed: 'Не удалось обработать файл',
};
