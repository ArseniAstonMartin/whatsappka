#!/bin/sh
# Согласованная копия PostgreSQL и медиа MinIO (PRD §11.3).
# На время снимка останавливаются записи: backend и worker — единственные процессы, которые пишут в БД и MinIO.
# Redis не копируется: он хранит кэш, ограничители частоты и краткоживущее состояние, источник данных — PostgreSQL и MinIO.
#
# Использование: infra/backup/backup.sh [каталог назначения]
# По умолчанию копия попадает в backups/<UTC-время>/ (каталог backups/ не попадает в Git и Docker-контекст).
#
# Настройка через переменные окружения (для VM или отдельного проекта compose):
#   COMPOSE_ARGS      аргументы docker compose, например: "--env-file /srv/whatsappka.env -f compose.yaml -f infra/coolify/compose.external.yaml"
#   MINIO_VOLUME      имя volume с объектами MinIO (по умолчанию whatsappka-minio)
#   BACKUP_DIR        корень для копий (по умолчанию backups/ в репозитории)
#   MIN_FREE_MB       минимум свободного места на диске назначения перед снимком (по умолчанию 1024)
set -eu

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

COMPOSE_ARGS=${COMPOSE_ARGS:--f compose.yaml}
MINIO_VOLUME=${MINIO_VOLUME:-whatsappka-minio}
MIN_FREE_MB=${MIN_FREE_MB:-1024}

# Намеренно без кавычек: COMPOSE_ARGS содержит несколько аргументов.
compose() { docker compose $COMPOSE_ARGS "$@"; }

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
DEST=${1:-${BACKUP_DIR:-$ROOT/backups}/$STAMP}

sha() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$@"; else shasum -a 256 "$@"; fi
}

# Образ с tar и psql: тот же закреплённый по digest образ PostgreSQL, что в compose.yaml.
HELPER_IMAGE=$(compose config --images | grep '^postgres:' | head -n 1)

WRITERS=""
for service in backend worker; do
    if compose ps --status running --services | grep -qx "$service"; then
        WRITERS="$WRITERS $service"
    fi
done

CREATED_DEST=0
restore_writers() {
    status=$?
    if [ "$status" -ne 0 ] && [ "$CREATED_DEST" -eq 1 ]; then
        echo "копия не завершена, неполный каталог удалён: $DEST" >&2
        rm -rf "$DEST"
    fi
    if [ -n "$WRITERS" ]; then
        # --no-deps: поднимаем только писатели, без повторного запуска migrate и minio-init.
        compose up -d --no-deps $WRITERS >/dev/null
        echo "записи возобновлены:$WRITERS"
    fi
    exit "$status"
}
trap restore_writers EXIT

if [ -e "$DEST" ]; then
    echo "каталог копии уже существует: $DEST" >&2
    exit 1
fi
PARENT=$(dirname "$DEST")
mkdir -p "$PARENT"
FREE_MB=$(df -Pm "$PARENT" | awk 'NR==2 {print $4}')
if [ "$FREE_MB" -lt "$MIN_FREE_MB" ]; then
    echo "мало места на диске назначения: ${FREE_MB} МиБ свободно, нужно не меньше ${MIN_FREE_MB}" >&2
    exit 1
fi
mkdir "$DEST"
CREATED_DEST=1

if [ -n "$WRITERS" ]; then
    echo "останавливаю записи:$WRITERS"
    # shellcheck disable=SC2086
    compose stop -t 60 $WRITERS >/dev/null
fi

echo "снимаю дамп PostgreSQL"
compose exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" exec pg_dump --format=custom --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$DEST/postgres.dump"

SCHEMA_VERSION=$(compose exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" exec psql -v ON_ERROR_STOP=1 -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL' | tr -d '\r'
SELECT CASE
    WHEN to_regclass('public.flyway_schema_history') IS NULL THEN 'none'
    ELSE coalesce((SELECT version FROM public.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1), 'none')
END;
SQL
)

echo "архивирую объекты MinIO (volume $MINIO_VOLUME)"
docker run --rm -v "$MINIO_VOLUME:/data:ro" -v "$DEST:/backup" --entrypoint tar "$HELPER_IMAGE" -C /data -czf /backup/minio-data.tar.gz .

BACKEND_IMAGE=$(docker image inspect --format '{{.Id}}' whatsappka-backend:local 2>/dev/null || echo "unknown")
cat > "$DEST/manifest.txt" <<EOF
format=whatsappka-backup-1
created_utc=$STAMP
schema_version=$SCHEMA_VERSION
postgres_image=$HELPER_IMAGE
backend_image_id=$BACKEND_IMAGE
postgres_dump=postgres.dump
minio_archive=minio-data.tar.gz
EOF

(cd "$DEST" && sha postgres.dump minio-data.tar.gz manifest.txt > SHA256SUMS)

echo "копия готова: $DEST (схема: $SCHEMA_VERSION)"
