FROM golang:1.24.8-bookworm@sha256:f5201dbbbfd769f9d2c034c0e0c06a91d08ea7a2cc44c12a9e009098d9fefc03 AS build

ARG MINIO_COMMIT=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
ARG MINIO_TAG=RELEASE.2025-10-15T17-29-55Z
ARG MC_COMMIT=7394ce0dd2a80935aded936b09fa12cbb3cb8096
ARG MC_TAG=RELEASE.2025-08-13T08-35-41Z

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
RUN git clone --depth 1 --branch "${MINIO_TAG}" https://github.com/minio/minio.git minio \
    && test "$(git -C minio rev-parse HEAD)" = "${MINIO_COMMIT}" \
    && git clone --depth 1 --branch "${MC_TAG}" https://github.com/minio/mc.git mc \
    && test "$(git -C mc rev-parse HEAD)" = "${MC_COMMIT}"

WORKDIR /src/minio
RUN MINIO_RELEASE=RELEASE go run buildscripts/gen-ldflags.go 2025-10-15T17-29-55Z > /tmp/ldflags \
    && CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags "$(cat /tmp/ldflags)" -o /out/minio .

WORKDIR /src/mc
RUN CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags "-s -w" -o /out/mc .

FROM debian:bookworm-slim@sha256:0c8bbb8e987a035fe1d9704eb2e571b7e9a836e1caa46345290674b45b69e417

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gosu \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1000 --home-dir /home/minio --create-home minio \
    && mkdir -p /data \
    && chown minio:minio /data

COPY --from=build /out/minio /usr/local/bin/minio
COPY --from=build /out/mc /usr/local/bin/mc
COPY minio-entrypoint.sh init-bucket.sh /usr/local/bin/
RUN chmod 755 /usr/local/bin/minio-entrypoint.sh /usr/local/bin/init-bucket.sh

VOLUME ["/data"]
EXPOSE 9000
ENTRYPOINT ["/usr/local/bin/minio-entrypoint.sh"]
CMD ["minio", "server", "/data", "--address", ":9000"]
