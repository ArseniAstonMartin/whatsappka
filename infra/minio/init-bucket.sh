#!/bin/sh
set -eu

: "${MINIO_ROOT_USER:?}"
: "${MINIO_ROOT_PASSWORD:?}"
: "${MINIO_ACCESS_KEY:?}"
: "${MINIO_SECRET_KEY:?}"
: "${MINIO_BUCKET:?}"

case "$MINIO_BUCKET" in
    *[!a-z0-9-]*)
        echo "имя bucket содержит недопустимые символы" >&2
        exit 1
        ;;
esac

if [ "$MINIO_ACCESS_KEY" = "$MINIO_ROOT_USER" ]; then
    echo "ключ приложения не должен совпадать с корневым пользователем" >&2
    exit 1
fi

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null

ready=0
i=0
while [ "$i" -lt 30 ]; do
    if mc admin info local >/dev/null 2>&1; then
        ready=1
        break
    fi
    i=$((i + 1))
    sleep 2
done
if [ "$ready" -ne 1 ]; then
    echo "MinIO не ответил" >&2
    exit 1
fi

mc mb --ignore-existing "local/${MINIO_BUCKET}" >/dev/null
mc anonymous set none "local/${MINIO_BUCKET}" >/dev/null

cat > /tmp/whatsappka-app.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetBucketLocation",
        "s3:ListBucket",
        "s3:ListBucketMultipartUploads"
      ],
      "Resource": ["arn:aws:s3:::${MINIO_BUCKET}"]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:AbortMultipartUpload",
        "s3:DeleteObject",
        "s3:GetObject",
        "s3:ListMultipartUploadParts",
        "s3:PutObject"
      ],
      "Resource": ["arn:aws:s3:::${MINIO_BUCKET}/*"]
    }
  ]
}
EOF

if ! mc admin policy info local whatsappka-app >/dev/null 2>&1; then
    mc admin policy create local whatsappka-app /tmp/whatsappka-app.json >/dev/null
fi

if ! mc admin user info local "$MINIO_ACCESS_KEY" >/dev/null 2>&1; then
    mc admin user add local "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null
fi

mc admin policy attach local whatsappka-app --user "$MINIO_ACCESS_KEY" >/dev/null
rm -f /tmp/whatsappka-app.json
echo "bucket ${MINIO_BUCKET} закрыт, ключ приложения ограничен этим bucket"
