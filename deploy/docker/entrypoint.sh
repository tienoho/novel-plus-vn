#!/bin/sh
set -eu

load_secret_file() {
    variable=$1
    eval "file=\${${variable}_FILE:-}"
    if [ -z "$file" ]; then
        return
    fi
    if [ ! -r "$file" ]; then
        echo "Không thể đọc secret file cho $variable: $file" >&2
        exit 1
    fi
    value=$(cat "$file")
    if [ -z "$value" ]; then
        echo "Secret file cho $variable đang trống" >&2
        exit 1
    fi
    export "$variable=$value"
}

for variable in DB_PASSWORD SPRING_DATA_REDIS_PASSWORD SPRING_REDIS_PASSWORD \
    JWT_SECRET CACHE_MANAGER_PASSWORD PII_ENCRYPTION_KEY \
    ADMIN_BOOTSTRAP_PASSWORD CRAWLER_ADMIN_PASSWORD \
    VNPAY_HASH_SECRET VNPAY_RECURRING_PASSWORD VNPAY_RECURRING_CLIENT_SECRET \
    VNPAY_RECURRING_HASH_SECRET VIETQR_WEBHOOK_SECRET; do
    load_secret_file "$variable"
done

. /usr/local/lib/novel/validate-secrets.sh
validate_production_secrets

required_vars="DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD"
for variable in $required_vars; do
    eval "value=\${$variable:-}"
    if [ -z "$value" ]; then
        echo "Thiếu biến môi trường bắt buộc: $variable" >&2
        exit 1
    fi
done

case "$DB_PORT" in
    *[!0-9]*|'')
        echo "DB_PORT phải là số nguyên dương" >&2
        exit 1
        ;;
esac

case "$DB_NAME" in
    *[!A-Za-z0-9_]*|'')
        echo "DB_NAME chỉ được chứa chữ cái, chữ số và dấu gạch dưới" >&2
        exit 1
        ;;
esac

DB_TIMEZONE=${DB_TIMEZONE:-Asia/Ho_Chi_Minh}
SHARDING_SQL_SHOW=${SHARDING_SQL_SHOW:-false}

case "$SHARDING_SQL_SHOW" in
    true|false) ;;
    *)
        echo "SHARDING_SQL_SHOW chỉ nhận true hoặc false" >&2
        exit 1
        ;;
esac

escape_replacement() {
    printf '%s' "$1" | sed -e "s/'/''/g" -e 's/[\\&|]/\\&/g'
}

template=/opt/novel/shardingsphere-jdbc.yml.template
output=/app/config/shardingsphere-jdbc.yml
temporary=${output}.tmp

umask 077
sed \
    -e "s|__DB_HOST__|$(escape_replacement "$DB_HOST")|g" \
    -e "s|__DB_PORT__|$(escape_replacement "$DB_PORT")|g" \
    -e "s|__DB_NAME__|$(escape_replacement "$DB_NAME")|g" \
    -e "s|__DB_USERNAME__|$(escape_replacement "$DB_USERNAME")|g" \
    -e "s|__DB_PASSWORD__|$(escape_replacement "$DB_PASSWORD")|g" \
    -e "s|__DB_TIMEZONE__|$(escape_replacement "$DB_TIMEZONE")|g" \
    -e "s|__SQL_SHOW__|$SHARDING_SQL_SHOW|g" \
    "$template" > "$temporary"
mv "$temporary" "$output"

exec java -jar /app/app.jar
