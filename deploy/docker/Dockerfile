# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /workspace

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Pcentral-repo -DskipTests -Dmaven.antrun.skip=true package

# Runtime là lớp nền dùng chung; file tồn tại trong theme được chép sau để ghi đè.
RUN set -eux; \
    mkdir -p /workspace/packaged-themes; \
    for theme in green orange dark blue; do \
        mkdir -p "/workspace/packaged-themes/$theme/html" "/workspace/packaged-themes/$theme/static"; \
        cp -a /workspace/novel-front/src/main/resources/templates/. "/workspace/packaged-themes/$theme/html/"; \
        cp -a /workspace/novel-front/src/main/resources/static/. "/workspace/packaged-themes/$theme/static/"; \
        cp -a "/workspace/templates/$theme/html/." "/workspace/packaged-themes/$theme/html/"; \
        cp -a "/workspace/templates/$theme/static/." "/workspace/packaged-themes/$theme/static/"; \
    done

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl fontconfig fonts-dejavu-core tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 novel \
    && useradd --uid 10001 --gid novel --home-dir /app --shell /usr/sbin/nologin novel \
    && mkdir -p /app/config /data/pic /data/books \
    && chown -R novel:novel /app /data

WORKDIR /app

COPY --chmod=0755 deploy/docker/entrypoint.sh /usr/local/bin/novel-entrypoint
COPY --chown=novel:novel deploy/docker/shardingsphere-jdbc.yml.template /opt/novel/shardingsphere-jdbc.yml.template

ENV TZ=Asia/Ho_Chi_Minh \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

ENTRYPOINT ["/usr/local/bin/novel-entrypoint"]

FROM runtime AS front
COPY --from=builder --chown=novel:novel /workspace/novel-front/target/novel-front-*.jar /app/app.jar
COPY --from=builder --chown=novel:novel /workspace/packaged-themes /app/templates
USER novel
EXPOSE 8083
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8083/ > /dev/null || exit 1

FROM runtime AS crawl
COPY --from=builder --chown=novel:novel /workspace/novel-crawl/target/novel-crawl-*.jar /app/app.jar
USER novel
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8081/login.html > /dev/null || exit 1

FROM runtime AS admin
COPY --from=builder --chown=novel:novel /workspace/novel-admin/target/novel-admin-*.jar /app/app.jar
USER novel
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD curl --fail --silent --show-error http://127.0.0.1/login > /dev/null || exit 1
