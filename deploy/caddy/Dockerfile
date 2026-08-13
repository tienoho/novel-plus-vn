# syntax=docker/dockerfile:1.7

ARG CADDY_VERSION=2.11.4
FROM caddy:${CADDY_VERSION}-builder-alpine AS builder

RUN xcaddy build \
    --with github.com/mholt/caddy-ratelimit@5625512f24f6f59d6f64fb3aafe5eecff0b286db \
    --replace golang.org/x/text=golang.org/x/text@v0.39.0 \
    --replace google.golang.org/grpc=google.golang.org/grpc@v1.82.1

ARG CADDY_VERSION=2.11.4
FROM caddy:${CADDY_VERSION}-alpine

RUN apk upgrade --no-cache

COPY --from=builder /usr/bin/caddy /usr/bin/caddy
COPY --chmod=0444 deploy/caddy/Caddyfile /etc/caddy/Caddyfile

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
