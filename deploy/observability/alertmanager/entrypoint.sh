#!/bin/sh
set -eu

secret_file=${ALERTMANAGER_WEBHOOK_URL_FILE:-/run/secrets/alertmanager_webhook_url}
if [ ! -r "$secret_file" ]; then
    echo "Không thể đọc webhook Alertmanager: $secret_file" >&2
    exit 1
fi

webhook_url=$(tr -d '\r\n' < "$secret_file")
case "$webhook_url" in
    https://*) ;;
    http://*)
        if [ "${ALERTMANAGER_ALLOW_HTTP:-false}" != "true" ]; then
            echo "Webhook Alertmanager production phải dùng HTTPS" >&2
            exit 1
        fi
        ;;
    *)
        echo "Webhook Alertmanager phải là URL HTTP(S) hợp lệ" >&2
        exit 1
        ;;
esac
case "$webhook_url" in
    *"'"*|*" "*|*"\t"*)
        echo "Webhook Alertmanager chứa ký tự không được hỗ trợ" >&2
        exit 1
        ;;
esac

escaped_url=$(printf '%s' "$webhook_url" | sed 's/[&|]/\\&/g')
sed "s|__ALERTMANAGER_WEBHOOK_URL__|$escaped_url|g" \
    /etc/alertmanager/alertmanager.yml.template > /tmp/alertmanager.yml

exec /bin/alertmanager \
    --config.file=/tmp/alertmanager.yml \
    --storage.path=/alertmanager \
    --web.listen-address=:9093
