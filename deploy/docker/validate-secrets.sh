#!/bin/sh

reject_sample_secret() {
    case "$1" in
        change-me*|CHANGE-ME*|disabled|DISABLED|test123456|novel123456|admin|ADMIN|password|PASSWORD|secret|SECRET)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

require_secret() {
    variable=$1
    minimum_length=$2
    eval "value=\${$variable:-}"
    if [ -z "$value" ] || [ "${#value}" -lt "$minimum_length" ] || reject_sample_secret "$value"; then
        echo "$variable phải là secret ngẫu nhiên có ít nhất $minimum_length ký tự và không phải giá trị mẫu" >&2
        exit 1
    fi
}

require_base64_aes256_key() {
    variable=$1
    eval "value=\${$variable:-}"
    if [ -z "$value" ] || reject_sample_secret "$value"; then
        echo "$variable phải là khóa AES-256 Base64, không được để trống hoặc dùng giá trị mẫu" >&2
        exit 1
    fi
    decoded_length=$(printf '%s' "$value" | base64 -d 2>/dev/null | wc -c | tr -d ' ')
    if [ "$decoded_length" != "32" ]; then
        echo "$variable phải là Base64 của đúng 32 byte" >&2
        exit 1
    fi
}

require_enabled_secret() {
    enabled_variable=$1
    secret_variable=$2
    minimum_length=$3
    eval "enabled=\${$enabled_variable:-false}"
    case "$enabled" in
        true|TRUE|1)
            require_secret "$secret_variable" "$minimum_length"
            ;;
        false|FALSE|0|'')
            ;;
        *)
            echo "$enabled_variable chỉ nhận true hoặc false" >&2
            exit 1
            ;;
    esac
}

require_value_when_enabled() {
    enabled_variable=$1
    value_variable=$2
    eval "enabled=\${$enabled_variable:-false}"
    eval "value=\${$value_variable:-}"
    case "$enabled" in
        true|TRUE|1)
            if [ -z "$value" ] || reject_sample_secret "$value"; then
                echo "$value_variable phải được cấu hình khi bật $enabled_variable" >&2
                exit 1
            fi
            ;;
        false|FALSE|0|'')
            ;;
        *)
            echo "$enabled_variable chỉ nhận true hoặc false" >&2
            exit 1
            ;;
    esac
}

validate_production_secrets() {
    require_secret DB_PASSWORD 16

    case "${NOVEL_SERVICE_ROLE:-}" in
        front)
            require_secret SPRING_DATA_REDIS_PASSWORD 16
            require_secret JWT_SECRET 32
            require_secret CACHE_MANAGER_PASSWORD 16
            require_base64_aes256_key PII_ENCRYPTION_KEY
            require_enabled_secret VNPAY_ENABLED VNPAY_HASH_SECRET 32
            require_value_when_enabled VNPAY_RECURRING_ENABLED VNPAY_RECURRING_CLIENT_ID
            require_value_when_enabled VNPAY_RECURRING_ENABLED VNPAY_RECURRING_USERNAME
            require_enabled_secret VNPAY_RECURRING_ENABLED VNPAY_RECURRING_PASSWORD 12
            require_enabled_secret VNPAY_RECURRING_ENABLED VNPAY_RECURRING_CLIENT_SECRET 16
            require_value_when_enabled VNPAY_RECURRING_ENABLED VNPAY_RECURRING_TMN_CODE
            require_enabled_secret VNPAY_RECURRING_ENABLED VNPAY_RECURRING_HASH_SECRET 32
            require_enabled_secret VIETQR_ENABLED VIETQR_WEBHOOK_SECRET 32
            ;;
        admin)
            require_secret SPRING_REDIS_PASSWORD 16
            require_secret ADMIN_BOOTSTRAP_PASSWORD 12
            require_base64_aes256_key PII_ENCRYPTION_KEY
            ;;
        crawl)
            require_secret SPRING_DATA_REDIS_PASSWORD 16
            require_secret CRAWLER_ADMIN_PASSWORD 16
            ;;
        *)
            echo "NOVEL_SERVICE_ROLE không hợp lệ" >&2
            exit 1
            ;;
    esac
}
