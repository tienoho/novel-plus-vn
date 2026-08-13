#!/bin/sh
set -eu

database=${MYSQL_DATABASE:-novel_plus}
case "$database" in
    *[!A-Za-z0-9_]*|'')
        echo "MYSQL_DATABASE chỉ được chứa chữ cái, chữ số và dấu gạch dưới" >&2
        exit 1
        ;;
esac

archive=${BACKUP_ARCHIVE:-}
case "$archive" in
    ''|*/*|*\\*)
        echo "BACKUP_ARCHIVE phải là tên một tệp .tar.gz.gpg trong /backups" >&2
        exit 1
        ;;
esac
encrypted="/backups/$archive"
if [ ! -r "$encrypted" ]; then
    echo "Không tìm thấy bản sao lưu: $encrypted" >&2
    exit 1
fi

password_file=${MYSQL_PASSWORD_FILE:-/run/secrets/mysql_root_password}
encryption_file=${BACKUP_ENCRYPTION_PASSWORD_FILE:-/run/secrets/backup_encryption_password}
for file in "$password_file" "$encryption_file"; do
    if [ ! -r "$file" ] || [ ! -s "$file" ]; then
        echo "Secret file không đọc được hoặc đang trống: $file" >&2
        exit 1
    fi
done

umask 077
temporary=$(mktemp -d /work/novel-restore.XXXXXX)
GNUPGHOME="$temporary/gnupg"
mkdir -m 0700 "$GNUPGHOME"
export GNUPGHOME
cleanup_database=''
cleanup() {
    if [ -n "$cleanup_database" ]; then
        MYSQL_PWD=$(cat "$password_file") mysql \
            --host="${MYSQL_HOST:-mysql}" --port="${MYSQL_PORT:-3306}" \
            --user="${MYSQL_USER:-root}" \
            -e "DROP DATABASE IF EXISTS \`$cleanup_database\`" >/dev/null 2>&1 || true
    fi
    rm -rf "$temporary"
}
trap cleanup EXIT INT TERM

gpg --batch --yes --pinentry-mode loopback \
    --passphrase-file "$encryption_file" \
    --decrypt --output "$temporary/archive.tar.gz" "$encrypted"
tar -xzf "$temporary/archive.tar.gz" -C "$temporary"
(
    cd "$temporary"
    sha256sum -c SHA256SUMS
)

if [ "${RESTORE_DRILL:-false}" = "true" ]; then
    if [ "${RESTORE_CONFIRM:-}" != "RESTORE_DRILL" ]; then
        echo "RESTORE_CONFIRM=RESTORE_DRILL là bắt buộc cho bài diễn tập" >&2
        exit 1
    fi
    target="${database}_restore_drill"
    cleanup_database=$target
else
    if [ "${RESTORE_CONFIRM:-}" != "RESTORE" ]; then
        echo "RESTORE_CONFIRM=RESTORE là bắt buộc; thao tác này thay thế database và file hiện tại" >&2
        exit 1
    fi
    target=$database
fi

MYSQL_PWD=$(cat "$password_file") mysql \
    --host="${MYSQL_HOST:-mysql}" --port="${MYSQL_PORT:-3306}" \
    --user="${MYSQL_USER:-root}" \
    -e "DROP DATABASE IF EXISTS \`$target\`; CREATE DATABASE \`$target\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
MYSQL_PWD=$(cat "$password_file") mysql \
    --host="${MYSQL_HOST:-mysql}" --port="${MYSQL_PORT:-3306}" \
    --user="${MYSQL_USER:-root}" "$target" < "$temporary/database.sql"

table_count=$(MYSQL_PWD=$(cat "$password_file") mysql \
    --host="${MYSQL_HOST:-mysql}" --port="${MYSQL_PORT:-3306}" \
    --user="${MYSQL_USER:-root}" --batch --skip-column-names \
    -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$target'")
if [ "$table_count" -lt 1 ]; then
    echo "Database phục hồi không có bảng" >&2
    exit 1
fi

if [ "${RESTORE_DRILL:-false}" = "true" ]; then
    echo "Restore drill thành công với $table_count bảng"
    exit 0
fi

for directory in /data/media /data/books; do
    if [ ! -d "$directory" ]; then
        echo "Thiếu mount phục hồi bắt buộc: $directory" >&2
        exit 1
    fi
    find "$directory" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
done
tar -xzf "$temporary/files.tar.gz" -C /data
echo "Phục hồi hoàn tất vào database $target"
