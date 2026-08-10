# syntax=docker/dockerfile:1.7

ARG PUSHGATEWAY_VERSION=1.11.3
ARG PUSHGATEWAY_COMMIT=e803ebd81be5867ff17a21205030611fa033af13

FROM golang:1.26.5-alpine@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2 AS builder

ARG TARGETOS=linux
ARG TARGETARCH=amd64
ARG PUSHGATEWAY_VERSION
ARG PUSHGATEWAY_COMMIT

RUN apk add --no-cache git \
    && git clone --depth 1 --branch "v${PUSHGATEWAY_VERSION}" \
        https://github.com/prometheus/pushgateway.git /src \
    && test "$(git -C /src rev-parse HEAD)" = "${PUSHGATEWAY_COMMIT}"

WORKDIR /src

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go get golang.org/x/sync@v0.21.0 golang.org/x/text@v0.39.0 \
    && go mod tidy \
    && test "$(go list -m -f '{{.Version}}' golang.org/x/sync)" = v0.21.0 \
    && test "$(go list -m -f '{{.Version}}' golang.org/x/text)" = v0.39.0 \
    && go test ./... \
    && mkdir -p /out \
    && flags="-s -w \
        -X github.com/prometheus/common/version.Version=${PUSHGATEWAY_VERSION} \
        -X github.com/prometheus/common/version.Revision=${PUSHGATEWAY_COMMIT} \
        -X github.com/prometheus/common/version.Branch=HEAD \
        -X github.com/prometheus/common/version.BuildUser=novel-plus \
        -X github.com/prometheus/common/version.BuildDate=20260809-000000" \
    && CGO_ENABLED=0 GOOS="${TARGETOS}" GOARCH="${TARGETARCH}" \
        go build -trimpath -buildvcs=false -ldflags="$flags" -o /out/pushgateway .

ARG PUSHGATEWAY_VERSION
FROM docker.io/prom/pushgateway:v${PUSHGATEWAY_VERSION}@sha256:74fa117cef2d7e383112d25139ff1c2d2e309c35389a9e0554a47136a1482e48

COPY --from=builder --chown=65534:65534 --chmod=0555 /out/pushgateway /bin/pushgateway
