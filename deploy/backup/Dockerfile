# syntax=docker/dockerfile:1.7

FROM mysql:8.4

RUN rm -rf /usr/lib/mysqlsh \
    && rm -f /usr/bin/mysqlsh /usr/local/bin/gosu \
    && groupadd --gid 10002 backup \
    && useradd --uid 10002 --gid backup --no-create-home --shell /sbin/nologin backup \
    && mkdir -p /backups /work /data/media /data/books \
    && chown -R backup:backup /backups /work

COPY --chmod=0555 deploy/backup/backup.sh /usr/local/bin/novel-backup
COPY --chmod=0555 deploy/backup/restore.sh /usr/local/bin/novel-restore

USER backup
WORKDIR /backups
