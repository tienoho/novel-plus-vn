#!/bin/sh
set -eu

if [ -n "${FLYWAY_PASSWORD_FILE:-}" ]; then
    if [ ! -r "$FLYWAY_PASSWORD_FILE" ]; then
        echo "Không thể đọc FLYWAY_PASSWORD_FILE: $FLYWAY_PASSWORD_FILE" >&2
        exit 1
    fi
    FLYWAY_PASSWORD=$(cat "$FLYWAY_PASSWORD_FILE")
    export FLYWAY_PASSWORD
fi

case "${FLYWAY_PASSWORD:-}" in
    ''|change-me*|CHANGE-ME*|test123456|password|PASSWORD)
        echo "FLYWAY_PASSWORD phải là secret production và không phải giá trị mẫu" >&2
        exit 1
        ;;
esac
if [ "${#FLYWAY_PASSWORD}" -lt 16 ]; then
    echo "FLYWAY_PASSWORD phải có ít nhất 16 ký tự" >&2
    exit 1
fi

push_migration_status() {
    status=$1
    if [ -z "${PUSHGATEWAY_URL:-}" ]; then
        return 0
    fi
    timestamp=$(date +%s)
    if ! printf 'novel_migration_success %s\nnovel_migration_last_run_timestamp_seconds %s\n' \
        "$status" "$timestamp" | curl --fail --silent --show-error --retry 3 \
        --retry-connrefused --data-binary @- \
        "${PUSHGATEWAY_URL%/}/metrics/job/novel_migrations/instance/compose"; then
        echo "Cảnh báo: không thể gửi metric migration tới Pushgateway" >&2
    fi
}

on_exit() {
    exit_code=$?
    if [ "$exit_code" -ne 0 ]; then
        push_migration_status 0
    fi
    exit "$exit_code"
}
trap on_exit EXIT

flyway migrate
flyway validate
push_migration_status 1
trap - EXIT
