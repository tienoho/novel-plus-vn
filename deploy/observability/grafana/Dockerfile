# syntax=docker/dockerfile:1.7

ARG GRAFANA_VERSION=13.1.3
ARG GRAFANA_COMMIT=45a27d64b64a82d666b06aa5c5bb3521587edb0d
ARG GRAFANA_SOURCE_DATE_EPOCH=1786063404

FROM docker.io/library/golang:1.26.5-bookworm@sha256:6c5605ab3a9a9fb3c4eafe5b3d63cdbf3881caf113262b67862547b54a9db599 AS builder

ARG GRAFANA_VERSION
ARG GRAFANA_COMMIT
ARG GRAFANA_SOURCE_DATE_EPOCH
ARG GRAFANA_GO_BUILD_PARALLELISM=2
ARG TARGETOS=linux
ARG TARGETARCH=amd64

ENV GOMAXPROCS=${GRAFANA_GO_BUILD_PARALLELISM} \
    GOFLAGS=-p=${GRAFANA_GO_BUILD_PARALLELISM}

RUN git clone --depth 1 --branch "v${GRAFANA_VERSION}" \
      https://github.com/grafana/grafana.git /src \
    && test "$(git -C /src rev-parse HEAD)" = "${GRAFANA_COMMIT}"

WORKDIR /src
COPY deploy/observability/grafana/grafana-no-tempo.patch /tmp/grafana-no-tempo.patch

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    git apply --check /tmp/grafana-no-tempo.patch \
    && git apply /tmp/grafana-no-tempo.patch \
    && go run ./pkg/build/wire/cmd/wire/main.go \
         gen -tags oss -gen_tags '(!enterprise && !pro)' ./pkg/server \
    && make build-go \
         GO_BUILD_TAGS=oss \
         WIRE_TAGS=oss \
         OS="${TARGETOS}" \
         ARCH="${TARGETARCH}" \
         COMMIT_SHA="${GRAFANA_COMMIT}" \
         BUILD_BRANCH="v${GRAFANA_VERSION}" \
         SOURCE_DATE_EPOCH="${GRAFANA_SOURCE_DATE_EPOCH}" \
    && go version -m "./bin/${TARGETOS}/${TARGETARCH}/grafana" \
         | tee /tmp/grafana-build-metadata.txt \
    && ! grep -q 'github.com/grafana/tempo' /tmp/grafana-build-metadata.txt \
    && "./bin/${TARGETOS}/${TARGETARCH}/grafana" --version \
         | grep -F "grafana version ${GRAFANA_VERSION}"

FROM docker.io/grafana/grafana:13.1.3@sha256:ab5cb380e3ff3172d6c8bd2e7cfd31cce977d2881b260e1f5bc089bf0b759b43

ARG TARGETOS=linux
ARG TARGETARCH=amd64

USER root
COPY --from=builder --chmod=0555 /src/bin/${TARGETOS}/${TARGETARCH}/grafana /usr/share/grafana/bin/grafana
COPY --chmod=0555 deploy/observability/grafana/entrypoint.sh /usr/local/bin/novel-grafana-entrypoint
RUN rm -rf \
      /usr/share/grafana/public/app/plugins/datasource/tempo \
      /usr/share/grafana/public/app/plugins/datasource/elasticsearch \
      /usr/share/grafana/public/app/plugins/datasource/zipkin \
      /usr/share/grafana/data/plugins-bundled/elasticsearch \
      /usr/share/grafana/data/plugins-bundled/zipkin \
    && test ! -e /usr/share/grafana/public/app/plugins/datasource/tempo \
    && test ! -e /usr/share/grafana/data/plugins-bundled/elasticsearch \
    && test ! -e /usr/share/grafana/data/plugins-bundled/zipkin
ENV GF_PLUGINS_PREINSTALL_DISABLED=true \
    GF_PLUGINS_DISABLE_PLUGINS=tempo,elasticsearch,zipkin
USER 472
ENTRYPOINT ["/usr/local/bin/novel-grafana-entrypoint"]
