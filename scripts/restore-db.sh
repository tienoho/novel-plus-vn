#!/usr/bin/env bash
# Database Restore Script for Novel-Plus MySQL Database
set -e

if [ -z "$1" ]; then
    echo "[ERROR] Usage: $0 <path_to_backup_file.sql.gz|path_to_backup_file.sql>"
    exit 1
fi

BACKUP_FILE="$1"
if [ ! -f "${BACKUP_FILE}" ]; then
    echo "[ERROR] Backup file does not exist: ${BACKUP_FILE}"
    exit 1
fi

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${MYSQL_USER:-root}"
DB_PASS="${MYSQL_ROOT_PASSWORD:-root123456}"
DB_NAME="${MYSQL_DATABASE:-novel_plus}"

echo "[INFO] Restoring database '${DB_NAME}' from '${BACKUP_FILE}'..."

if [[ "${BACKUP_FILE}" == *.gz ]]; then
    if command -v docker &> /dev/null && docker ps | grep -q novel-plus-mysql; then
        gunzip -c "${BACKUP_FILE}" | docker exec -i novel-plus-mysql mysql --default-character-set=utf8mb4 -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}"
    else
        gunzip -c "${BACKUP_FILE}" | mysql --host="${DB_HOST}" --port="${DB_PORT}" --default-character-set=utf8mb4 -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}"
    fi
else
    if command -v docker &> /dev/null && docker ps | grep -q novel-plus-mysql; then
        docker exec -i novel-plus-mysql mysql --default-character-set=utf8mb4 -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" < "${BACKUP_FILE}"
    else
        mysql --host="${DB_HOST}" --port="${DB_PORT}" --default-character-set=utf8mb4 -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" < "${BACKUP_FILE}"
    fi
fi

echo "[SUCCESS] Database restoration completed successfully."
