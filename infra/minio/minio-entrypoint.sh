#!/bin/sh
set -eu

mkdir -p /data
chown minio:minio /data
exec gosu minio "$@"
