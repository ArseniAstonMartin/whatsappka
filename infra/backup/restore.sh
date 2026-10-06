#!/bin/sh
# Восстановление копии, созданной backup.sh (PRD §11.3).
# По умолчанию восстанавливает только в пустые volumes: если в них уже есть данные, скрипт ничего не меняет и выходит с ошибкой.
# Замена существующих данных возможна только с явным флагом --replace-existing-data; тогда удаляются volumes PostgreSQL и MinIO.
# Redis не восстанавливается (кэш и краткоживущее состояние). Общие команды docker compose данные не удаляют.
#
# Использование: infra/backup/restore.sh [--replace-existing-data] <каталог копии>
# Настройка: COMPOSE_ARGS, POSTGRES_VOLUME, MINIO_VOLUME — те же, что в backup.sh.
set -eu

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

COMPOSE_ARGS=${COMPOSE_ARGS:--f compose.yaml}
POSTGRES_VOLUME=${POSTGRES_VOLUME:-whatsappka-postgres}
MINIO_VOLUME=${MINIO_VOLUME:-whatsappka-minio}

# Намеренно без кавычек: COMPOSE_ARGS содержит несколько аргументов.
compose() { docker compose $COMPOSE_ARGS "$@"; }

usage() {
    echo "использование: $0 [--replace-existing-data] <каталог копии>" >&2
    exit 2
}

REPLACE=0
SRC=""
while [ $# -gt 0 ]; do
    case "$1" in
        --replace-existing-data) REPLACE=1 ;;
        -*) usage ;;
        *) [ -z "$SRC" ] || usage; SRC=$1 ;;
    esac
    shift
done
[ -n "$SRC" ] || usage
SRC=$(cd "$SRC" && pwd)

sha_check() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum -c SHA256SUMS; else shasum -a 256 -c SHA256SUMS; fi
}

# 1. Целостность: все файлы копии совпадают с SHA256SUMS.
(cd "$SRC" && sha_check >/dev/null) || { echo "контрольные суммы копии не совпадают — восстановление отменено" >&2; exit 1; }

manifest_value() { sed -n "s/^$1=//p" "$SRC/manifest.txt"; }
[ "$(manifest_value format)" = "whatsappka-backup-1" ] || { echo "неизвестный формат копии" >&2; exit 1; }
BACKUP_SCHEMA=$(manifest_value schema_version)

# 2. Совместимость схемы: копия не может быть новее миграций этой версии кода.
# Откат приложения на старый образ возможен только если схема в копии не новее миграций в репозитории (PRD §11.3).
REPO_SCHEMA=$(ls backend/src/main/resources/db/migration | sed -n 's/^V\([0-9][0-9]*\)__.*\.sql$/\1/p' | sort -n | tail -n 1)
if [ "$BACKUP_SCHEMA" != "none" ]; then
    case "$BACKUP_SCHEMA" in
        ''|*[!0-9]*) echo "версия схемы в копии не распознана: $BACKUP_SCHEMA" >&2; exit 1 ;;
    esac
    if [ "$BACKUP_SCHEMA" -gt "$REPO_SCHEMA" ]; then
        echo "схема в копии (V$BACKUP_SCHEMA) новее миграций этой версии кода (до V$REPO_SCHEMA): используйте образ той же или более новой версии" >&2
        exit 1
    fi
fi

# Образ с tar и psql: тот же закреплённый по digest образ PostgreSQL, что в compose.yaml.
HELPER_IMAGE=$(compose config --images | grep '^postgres:' | head -n 1)

volume_has_data() {
    docker volume inspect "$1" >/dev/null 2>&1 || return 1
    [ -n "$(docker run --rm -v "$1:/data:ro" --entrypoint sh "$HELPER_IMAGE" -c 'ls -A /data | head -n 1')" ]
}

# 3. Цель: данные в volumes проверяются до любых изменений.
NONEMPTY=""
for volume in "$POSTGRES_VOLUME" "$MINIO_VOLUME"; do
    if volume_has_data "$volume"; then NONEMPTY="$NONEMPTY $volume"; fi
done
if [ -n "$NONEMPTY" ] && [ "$REPLACE" -ne 1 ]; then
    echo "в volumes уже есть данные:$NONEMPTY" >&2
    echo "восстановление отменено. Для замены данных повторите с --replace-existing-data (текущие данные будут удалены)." >&2
    exit 1
fi

echo "останавливаю стек (volumes сохраняются)"
compose down

if [ -n "$NONEMPTY" ]; then
    echo "удаляю текущие данные по явному запросу:$NONEMPTY"
    for volume in $NONEMPTY; do docker volume rm "$volume" >/dev/null; done
fi

echo "создаю контейнеры PostgreSQL и MinIO (volumes создаются compose)"
compose create postgres minio >/dev/null

echo "восстанавливаю объекты MinIO"
docker run --rm -v "$MINIO_VOLUME:/data" -v "$SRC:/backup:ro" --entrypoint tar "$HELPER_IMAGE" -C /data -xzf /backup/minio-data.tar.gz

echo "восстанавливаю PostgreSQL"
compose up -d --wait postgres
compose exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" exec pg_restore --exit-on-error --single-transaction --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$SRC/postgres.dump"

RESTORED_SCHEMA=$(compose exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" exec psql -v ON_ERROR_STOP=1 -tA --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL' | tr -d '\r'
SELECT CASE
    WHEN to_regclass('public.flyway_schema_history') IS NULL THEN 'none'
    ELSE coalesce((SELECT version FROM public.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1), 'none')
END;
SQL
)
if [ "$RESTORED_SCHEMA" != "$BACKUP_SCHEMA" ]; then
    echo "версия схемы после восстановления ($RESTORED_SCHEMA) не совпадает с копией ($BACKUP_SCHEMA)" >&2
    exit 1
fi

echo "запускаю MinIO"
compose up -d --wait minio

echo "восстановлено из $SRC (схема: $RESTORED_SCHEMA)"
echo "дальше: docker compose up -d — миграции проверят контрольные суммы применённых версий перед стартом backend"
