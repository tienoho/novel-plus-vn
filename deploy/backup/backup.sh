#!/bin/sh
set -eu

database=${MYSQL_DATABASE:-novel_plus}
case "$database" in
    *[!A-Za-z0-9_]*|'')
        echo "MYSQL_DATABASE chỉ được chứa chữ cái, chữ số và dấu gạch dưới" >&2
        exit 1
        ;;
esac

password_file=${MYSQL_PASSWORD_FILE:-/run/secrets/mysql_root_password}
encryption_file=${BACKUP_ENCRYPTION_PASSWORD_FILE:-/run/secrets/backup_encryption_password}
for file in "$password_file" "$encryption_file"; do
    if [ ! -r "$file" ] || [ ! -s "$file" ]; then
        echo "Secret file không đọc được hoặc đang trống: $file" >&2
        exit 1
    fi
done

umask 077
temporary=$(mktemp -d /work/novel-backup.XXXXXX)
trap 'rm -rf "$temporary"' EXIT INT TERM
GNUPGHOME="$temporary/gnupg"
mkdir -m 0700 "$GNUPGHOME"
export GNUPGHOME
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
archive="khoi-thu-${timestamp}.tar.gz"
encrypted="/backups/${archive}.gpg"

MYSQL_PWD=$(cat "$password_file") mysqldump \
    --host="${MYSQL_HOST:-mysql}" \
    --port="${MYSQL_PORT:-3306}" \
    --user="${MYSQL_USER:-root}" \
    --single-transaction --routines --triggers --events --hex-blob \
    "$database" > "$temporary/database.sql"

tar -C /data -czf "$temporary/files.tar.gz" media books
(
    cd "$temporary"
    sha256sum database.sql files.tar.gz > SHA256SUMS
    tar -czf "$archive" database.sql files.tar.gz SHA256SUMS
)

gpg --batch --yes --pinentry-mode loopback \
    --passphrase-file "$encryption_file" \
    --symmetric --cipher-algo AES256 \
    --output "${encrypted}.tmp" "$temporary/$archive"
mv "${encrypted}.tmp" "$encrypted"
echo "$encrypted"
