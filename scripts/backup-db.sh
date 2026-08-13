#!/usr/bin/env bash
# Database Backup Script for Khoi-Thu MySQL Database
set -e

BACKUP_DIR="${BACKUP_DIR:-./backups}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/novel_plus_backup_${TIMESTAMP}.sql.gz"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${MYSQL_USER:-root}"
DB_PASS="${MYSQL_ROOT_PASSWORD:-root123456}"
DB_NAME="${MYSQL_DATABASE:-novel_plus}"

mkdir -p "${BACKUP_DIR}"

echo "[INFO] Starting database backup for database '${DB_NAME}' at ${TIMESTAMP}..."

if command -v docker &> /dev/null && docker ps | grep -q khoi-thu-mysql; then
    echo "[INFO] Performing dump via Docker container 'khoi-thu-mysql'..."
    docker exec khoi-thu-mysql mysqldump --default-character-set=utf8mb4 --routines --triggers --single-transaction -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" | gzip > "${BACKUP_FILE}"
else
    echo "[INFO] Performing dump via local mysqldump command..."
    mysqldump --host="${DB_HOST}" --port="${DB_PORT}" --default-character-set=utf8mb4 --routines --triggers --single-transaction -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" | gzip > "${BACKUP_FILE}"
fi

echo "[SUCCESS] Backup completed successfully. File saved at: ${BACKUP_FILE}"
