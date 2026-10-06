# Развёртывание через Coolify (внешний контур)

> Статус: файлы подготовлены, **стенд не развёрнут**. Ни один шаг ниже не выполнен на внешней VM.
> Этот документ описывает, что сделать владельцу, и что уже проверено локально.

## 1. Топология

- Одна Ubuntu LTS ARM64 VM (PRD §11.1): Docker Engine, Coolify, Compose-стек приложения.
- Наружу выходит только статика фронтенда (порт 80 внутри сети). Coolify ставит перед ней TLS и маршрут домена.
- Внутри одной сети compose (`private`, без выхода наружу): PostgreSQL, Redis, MinIO, одноразовые `migrate` и `minio-init`, почта `mailpit` (только локально).
- Сеть `edge` (с выходом наружу) — только у `backend`, `worker` и `frontend`: нужна для Google OIDC и SMTP.
- Порты баз, Redis, MinIO и backend на хост не публикуются: это делает `infra/coolify/compose.external.yaml`.

Порядок запуска: `migrate` и `minio-init` завершаются успешно → `backend` (healthy по readiness) → `worker` и `frontend`.

## 2. Два контура: локальный и внешний

Один и тот же commit и один `compose.yaml` работают в обоих контурах; различаются только переменные.

| Переменная | Локально | Внешний контур |
|---|---|---|
| `APP_URL` | `http://localhost:4200` | `https://<домен>` |
| `ALLOWED_ORIGINS` | `http://localhost:4200` | `https://<домен>` (WSS идёт по тому же origin, `/ws`) |
| `GOOGLE_REDIRECT_URI` | `http://localhost:4200/api/v1/auth/google/callback` | `https://<домен>/api/v1/auth/google/callback` |
| `REFRESH_COOKIE_SECURE` | `false` (вход по http в локальной сети) | **`true`** (cookie только по HTTPS) |
| `GOOGLE_CLIENT_ID/SECRET` | клиент для localhost | **отдельный** клиент для домена |
| `SMTP_*` | можно `mailpit` (профиль `mail`) | реальный SMTP провайдера владельца |
| `BOOTSTRAP_ADMIN_*` | по необходимости | только на первый запуск, затем очистить |

Важно: нельзя оставлять значения по умолчанию из `compose.yaml` во внешнем контуре. Там `localhost` и `REFRESH_COOKIE_SECURE=false`.

## 3. Переменные

Шаблон без значений: `infra/coolify/env.external.example`. Значения задаются в интерфейсе Coolify, в git их не коммитить.

Обязательные (compose падает без них): `POSTGRES_PASSWORD`, `DB_PASSWORD`, `DB_MIGRATION_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET` (не короче 32 байт), `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`.

Обязательные для внешнего контура по смыслу: `APP_URL`, `ALLOWED_ORIGINS`, `GOOGLE_REDIRECT_URI`, `REFRESH_COOKIE_SECURE=true`, `SMTP_*` (без SMTP письма подтверждения и сброса не уходят), `GOOGLE_CLIENT_ID/SECRET` (без них вход через Google честно отключён, вход по почте работает).

## 4. TLS и WSS

- TLS завершает прокси Coolify на домене владельца. Приложение внутри по http.
- `frontend` (nginx) проксирует `/api` и `/ws`. Для WebSocket прокси Coolify должен пропускать `Upgrade`. Проверить при первом запуске: подключение к `/ws` и кадр CONNECT.
- Заголовки безопасности: API отдаёт их сам (`SecurityHeaders`), статика — через `frontend/nginx.conf`. HSTS имеет смысл только за HTTPS: Coolify должен отдавать сайт по https.
- Адрес `ALLOWED_ORIGINS` должен совпадать с origin в браузере точно, включая схему.

## 5. Google OAuth

- Callback по пути `/api/v1/auth/google/callback` (контроллер `GoogleAuthController`). Для внешнего домена его нужно добавить в список разрешённых redirect URI клиента Google.
- Локальный клиент и клиент домена различаются. Не использовать один client secret для двух origin.
- Сбой и отказ Google возвращают пользователя на `/login?error=...` без токенов в адресе.

## 6. Шаги в Coolify (для владельца)

1. Создать ресурс «Docker Compose» из git-репозитория, ветка с нужным commit.
2. Указать compose-файлы: `compose.yaml` и `infra/coolify/compose.external.yaml`. Если интерфейс Coolify принимает только один файл, использовать его команду `docker compose -f compose.yaml -f infra/coolify/compose.external.yaml`. **Какой вариант поддерживает выбранная версия Coolify, не проверено** — проверить на стенде.
3. Внести переменные из `env.external.example` с реальными значениями.
4. Назначить домен сервису `frontend` с портом 80.
5. Первый запуск: заполнить `BOOTSTRAP_ADMIN_*`, после появления администратора очистить эти переменные и перезапустить.
6. Проверки: `GET https://<домен>/api/v1/auth/providers` → 200; вход по почте; обновление страницы не выкидывает из сессии; вход через Google (после настройки клиента); WebSocket-чат; загрузка файла.
7. Внутренние порты с внешнего адреса недоступны: проверить сканированием с другой сети, что открыт только 80/443.

## 7. Что проверено локально, а что нет

Проверено:
- `docker compose config` с `infra/coolify/env.external.example` и с `compose.external.yaml` проходит; после наложения override у баз, Redis, MinIO, backend и почты нет публикации портов, у frontend только `expose: 80`.
- Шаблон переменных не содержит значений по умолчанию с localhost и не содержит реальных секретов.
- Во фронтенде нет зашитых origin; URL относительные.

Не проверено:
- Сборка и запуск в Docker (по условию задачи полный стек не запускался).
- Поведение Coolify: выбор compose-файлов, прокси WebSocket, маршрут домена, TLS.
- Работа на ARM64 VM и доступность портов извне.

## 8. Открытые вопросы для владельца

- Домен или маршрут до VM (варианты: проброс через роутер при доступном адресе; HTTPS-туннель при CGNAT). Выбор не сделан.
- Инструмент VM (PRD §11.1 оставляет выбор после проверки сети и ресурсов).
- Google OAuth-клиент для домена и список redirect URI.
- SMTP-провайдер и адрес отправителя.
- Образ MinIO: по PRD нужен зафиксированный исходный commit и проверенный ARM64-образ; см. `infra/minio/README.md`.
- Digest-пины: у `postgres`, `redis` и `flyway` они есть в `compose.yaml`; `mailpit` пока на `latest` (локальный профиль), а базовые образы Dockerfile закреплены digest индекса. Перед выкладкой проверить актуальность пинов.
- Внешняя копия backup (PRD §11.3) — задача TASK-090.
