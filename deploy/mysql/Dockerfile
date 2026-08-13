# syntax=docker/dockerfile:1.7

ARG GOSU_VERSION=1.19
ARG GOSU_COMMIT=6456aaa0f3c854d199d0f037f068eb97515b7513

FROM golang:1.26.5-alpine@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2 AS gosu-builder

ARG TARGETOS=linux
ARG TARGETARCH=amd64
ARG GOSU_VERSION
ARG GOSU_COMMIT

RUN apk add --no-cache git \
    && git clone --depth 1 --branch "${GOSU_VERSION}" https://github.com/tianon/gosu.git /src \
    && test "$(git -C /src rev-parse HEAD)" = "${GOSU_COMMIT}"

WORKDIR /src

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go get golang.org/x/sys@v0.45.0 \
    && go mod tidy \
    && test "$(go list -m -f '{{.Version}}' golang.org/x/sys)" = v0.45.0 \
    && go test ./... \
    && CGO_ENABLED=0 GOOS="${TARGETOS}" GOARCH="${TARGETARCH}" \
        go build -trimpath -buildvcs=true -ldflags="-d -w" -o /out/gosu .

FROM docker.io/library/mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb

RUN rm -rf /usr/lib/mysqlsh /usr/libexec/mysqlsh /usr/share/mysqlsh \
    && rm -f /usr/bin/mysqlsh

COPY --from=gosu-builder --chmod=0555 /out/gosu /usr/local/bin/gosu

RUN gosu --version \
    && test "$(gosu mysql id -un)" = mysql
