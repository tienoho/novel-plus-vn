# syntax=docker/dockerfile:1.7

ARG ALERTMANAGER_VERSION=0.33.1
ARG ALERTMANAGER_COMMIT=2c8da51e03f3dbbed24f9711ca2d76aab4eef9c5
ARG ALERTMANAGER_UI_SHA256=1f63344e196e47ba7bfe27276f44c1da77e39fb76493e42b2cf0a50ca8f04321

FROM golang:1.26.5-alpine@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2 AS builder

ARG TARGETOS=linux
ARG TARGETARCH
ARG ALERTMANAGER_VERSION
ARG ALERTMANAGER_COMMIT
ARG ALERTMANAGER_UI_SHA256

RUN apk add --no-cache git curl \
    && git clone --depth 1 --branch "v${ALERTMANAGER_VERSION}" \
    https://github.com/prometheus/alertmanager.git /src \
    && test "$(git -C /src rev-parse HEAD)" = "${ALERTMANAGER_COMMIT}" \
    && curl -fsSL \
    "https://github.com/prometheus/alertmanager/releases/download/v${ALERTMANAGER_VERSION}/alertmanager-web-ui-${ALERTMANAGER_VERSION}.tar.gz" \
    -o /tmp/alertmanager-ui.tar.gz \
    && echo "${ALERTMANAGER_UI_SHA256}  /tmp/alertmanager-ui.tar.gz" | sha256sum -c - \
    && mkdir -p /src/ui/app /out \
    && tar -xzf /tmp/alertmanager-ui.tar.gz -C /src/ui/app \
    && test -d /src/ui/app/dist

WORKDIR /src

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go get golang.org/x/crypto@v0.53.0 \
    golang.org/x/text@v0.39.0 \
    google.golang.org/grpc@v1.82.1 \
    && go mod tidy \
    && test "$(go list -m -f '{{.Version}}' golang.org/x/crypto)" = v0.53.0 \
    && test "$(go list -m -f '{{.Version}}' golang.org/x/text)" = v0.39.0 \
    && test "$(go list -m -f '{{.Version}}' google.golang.org/grpc)" = v1.82.1 \
    && flags="-s -w \
    -X github.com/prometheus/common/version.Version=${ALERTMANAGER_VERSION} \
    -X github.com/prometheus/common/version.Revision=${ALERTMANAGER_COMMIT} \
    -X github.com/prometheus/common/version.Branch=HEAD \
    -X github.com/prometheus/common/version.BuildUser=khoi-thu \
    -X github.com/prometheus/common/version.BuildDate=20260704-000000" \
    && CGO_ENABLED=0 GOOS="${TARGETOS}" GOARCH="${TARGETARCH}" \
    go build -trimpath -tags netgo -ldflags="$flags" -o /out/alertmanager ./cmd/alertmanager \
    && CGO_ENABLED=0 GOOS="${TARGETOS}" GOARCH="${TARGETARCH}" \
    go build -trimpath -tags netgo -ldflags="$flags" -o /out/amtool ./cmd/amtool

ARG ALERTMANAGER_VERSION
FROM quay.io/prometheus/alertmanager:v${ALERTMANAGER_VERSION}@sha256:9e082985f56f4c8c9f724e18f2288c6708f472e56a5286b8863d080434ea065d

USER root
COPY --from=builder --chmod=0555 /out/alertmanager /bin/alertmanager
COPY --from=builder --chmod=0555 /out/amtool /bin/amtool
COPY --chmod=0444 deploy/observability/alertmanager/alertmanager.yml.template /etc/alertmanager/alertmanager.yml.template
COPY --chmod=0555 deploy/observability/alertmanager/entrypoint.sh /usr/local/bin/novel-alertmanager
USER 65534

ENTRYPOINT ["/usr/local/bin/novel-alertmanager"]
