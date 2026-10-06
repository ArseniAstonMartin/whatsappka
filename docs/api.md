# API

Ватсапка предоставляет REST API под `/api/v1` и realtime-канал на STOMP поверх WebSocket.

Полная спецификация OpenAPI 3 — в [openapi.json](openapi.json). Её же отдаёт работающий сервер по адресу `/v3/api-docs`, интерактивная документация — по `/swagger-ui.html`. Спецификация собрана из контроллеров и DTO, поэтому описывает только реализованные операции.

## 1. Общие правила

- **Формат:** JSON в UTF-8. Даты и время — ISO 8601 в UTC. Идентификаторы — UUID строкой.
- **Аутентификация:** заголовок `Authorization: Bearer <access JWT>`. Access-токен короткоживущий; обновляется через `POST /api/v1/auth/refresh`. Refresh-токен передаётся в HttpOnly cookie, которая доступна только для операций аутентификации.
- **Публичные операции** (без токена): регистрация, вход, обновление сессии, выход, восстановление пароля, подтверждение почты, список способов входа, вход через Google.
- **Cookie-операции** защищены от CSRF: нужен `Origin` из списка разрешённых и `SameSite=Lax`.
- **Курсорная пагинация:** списки возвращают `items`, `nextCursor` и `hasMore`. Размер страницы ограничен 50 элементами.
- **Идемпотентность:** для отдельных операций создания нужен заголовок `Idempotency-Key`. Повтор с тем же ключом не создаёт дубль.
- **Ограничение частоты:** при превышении ответ `429`. Например, отправка сообщений — не более 30 в минуту на пользователя.

### Ошибки

Ошибки возвращаются как `application/problem+json` в одной форме:

```json
{
  "status": 400,
  "code": "validation_failed",
  "detail": "Проверьте поля запроса",
  "fieldErrors": [{ "field": "email", "message": "Обязательное поле" }],
  "traceId": "3f1c2a9e-0b7d-4a51-9d1e-6a2f8c4b7e10"
}
```

- `code` — машинный код: например, `validation_failed`, `registration_closed`, `account_suspended`.
- `traceId` совпадает с заголовком `X-Trace-Id` ответа. Его указывают при обращении в поддержку.

Коды статусов: `400` неверный запрос, `401` нет действующей сессии, `403` действие запрещено, `404` объект не найден, `409` конфликт состояния, `413` слишком большой запрос, `415` неподдерживаемый тип содержимого, `429` слишком много запросов, `500` внутренняя ошибка.

## 2. Ресурсы REST

| Область | Примеры путей |
|---|---|
| Аутентификация | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `POST /auth/logout-all`, `GET /auth/providers` |
| Восстановление и почта | `POST /auth/password-reset/request`, `POST /auth/password-reset/confirm`, `POST /auth/email-confirmation/confirm`, `POST /me/email-confirmation` |
| Вход через Google | `GET /auth/google/start`, `GET /auth/google/callback` |
| Сессии | `GET /auth/sessions`, `DELETE /auth/sessions/{id}` |
| Профиль и текущий пользователь | `GET /me`, `GET/PATCH /me/profile`, `GET /users/{username}` |
| Лента и записи | `GET /feed`, `POST /posts`, `GET/PATCH/DELETE /posts/{id}`, `GET /me/post-drafts`, `GET /hashtags/{tag}/posts` |
| Комментарии и реакции | `GET/POST /posts/{postId}/comments`, `PUT/DELETE` реакций на записи и комментарии |
| Социальный граф | подписки и отписки, `GET /me/blocks` |
| Поиск | `GET /search/users`, `GET /search/posts`, `GET /search/groups`, `GET /search/hashtags` |
| Сообщества и группы | `GET /groups`, `POST /groups`, `GET /groups/slug/{slug}`, заявки и приглашения, `GET /me/groups` |
| Переписка | `GET /conversations`, `POST /conversations/groups`, `GET /conversations/{id}/messages`, правка и удаление сообщений, `GET /conversations/{id}/events` |
| Прочтение и присутствие | `GET /conversations/{id}/unread`, `GET /conversations/{id}/presence` |
| Медиа | `POST /media`, `GET /media/{id}/status`, `GET /media/{id}/content`, `POST /media/{id}/attach`, `POST /media/{id}/detach` |
| Уведомления | `GET /me/notifications`, `POST /me/notifications/read-all`, `GET /me/notification-preferences`, `PUT /me/notification-preferences/{type}` |
| Жалобы и модерация | `POST /reports`, `GET /moderation/reports`, `GET /moderation/actions`, `POST /moderation/users/{id}/sanctions` |
| Администрирование | `GET /admin/users`, `GET /admin/settings`, `PUT /admin/settings/{key}`, `GET /admin/stats`, `POST /admin/users/{id}/sanctions`, `POST /admin/sanctions/{id}/lift`, `POST /admin/jobs/{id}/retry`, `GET /moderation/audit` |

Точный набор параметров, тела запроса и схемы ответов — в OpenAPI.

## 3. Realtime: STOMP поверх WebSocket

### Подключение

- Адрес: `wss://<домен>/ws` (в локальной сборке — `ws://localhost:4200/ws`).
- Origin страницы должен быть в списке разрешённых.
- Кадр `CONNECT` содержит заголовок `Authorization: Bearer <access JWT>`. Без действующей сессии соединение отклоняется.
- Размер кадра — до 64 КБ. Heartbeat — каждые 10 секунд.
- Соединение закрывается сервером, когда истекает срок access-токена или отзывается сессия. Клиент должен переподключиться с новым токеном.

### Подписки

Разрешены только две очереди текущего пользователя. Любое другое назначение отклоняется.

| Назначение | Содержимое |
|---|---|
| `/user/queue/events` | события (см. ниже) |
| `/user/queue/errors` | ошибки команд |

### Команды

Разрешены три команды, все адресуются конкретной беседе:

| Назначение | Тело | Что происходит |
|---|---|---|
| `/app/conversations/{id}/messages` | `{"clientMessageId": "<uuid>", "body": "текст", "attachments": ["<uuid медиа>"]}` | сообщение сохраняется; отправителю приходит `message.saved` с `messageId`, `seq` и флагом `replayed`; участникам — `message.created` |
| `/app/conversations/{id}/typing` | без тела | остальным участникам уходит `typing.changed` с `expiresInSeconds: 5` |
| `/app/conversations/{id}/read` | `{"seq": <номер>}` | отмечается прочтение до указанного `seq`; уходит `conversation.read` |

Ошибка отправки приходит в `/user/queue/errors` с `clientMessageId` и `code`; клиент сопоставляет её с отправленным сообщением по `clientMessageId`.

### События

Каждое событие имеет оболочку:

| Поле | Смысл |
|---|---|
| `eventId` | уникальный идентификатор; клиент отбрасывает повторы (доставка «не менее одного раза») |
| `type` | тип, например `message.created`, `message.edited`, `message.deleted`, `typing.changed`, `conversation.read`, `notification.created` |
| `occurredAt` | время события, UTC |
| `entityId`, `entityVersion` | объект и его версия; клиент применяет событие, если версия новее |
| `conversationId` | беседа, если событие относится к ней |
| `eventSeq` | порядковый номер в журнале беседы; внутри беседы порядок задаёт `eventSeq` |
| `payload` | данные события |

Правка и удаление не меняют порядок сообщений и `eventSeq`.

### Восстановление после разрыва

1. Переподключитесь с актуальным токеном и заново подпишитесь на `/user/queue/events`.
2. Для каждой открытой беседы запросите текущую границу журнала: `GET /api/v1/conversations/{id}/events/head`.
3. Догоните пропуски: `GET /api/v1/conversations/{id}/events?cursor=<последний применённый eventSeq>&limit=50`, пока `hasMore` равно `false`.
4. Если ответ содержит `fullSyncRequired: true`, журнала недостаточно: перечитайте беседу через `GET /api/v1/conversations/{id}/messages`.
5. Применяйте события, пропуская уже известные `eventId`.

Курсор `cursor` — номер последнего применённого события, поэтому повторный запрос безопасен.
