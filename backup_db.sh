#!/usr/bin/env bash
# ====================================================================
# UN POS - PostgreSQL Automated Database Backup Script
# Creates a compressed backup of the 'pos_db' database.
# ====================================================================

set -e

BACKUP_DIR="./backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/pos_db_${TIMESTAMP}.sql.gz"

mkdir -p "$BACKUP_DIR"

echo "Creating PostgreSQL backup..."

# Extract credentials from .env if present
if [ -f ".env" ]; then
  export $(grep -v '^#' .env | xargs)
fi

DB_NAME="pos_db"
DB_USER="pos_user"

# Execute pg_dump
if command -v pg_dump >/dev/null 2>&1; then
  sudo -u postgres pg_dump -F c "$DB_NAME" | gzip > "$BACKUP_FILE" 2>/dev/null || \
  pg_dump "$DATABASE_URL" | gzip > "$BACKUP_FILE"
  echo "✓ Backup created successfully at: $BACKUP_FILE"
  echo "File size: $(du -h "$BACKUP_FILE" | cut -f1)"
else
  echo "Error: pg_dump command not found."
  exit 1
fi

# Keep only last 14 days of backups
find "$BACKUP_DIR" -type f -name "*.sql.gz" -mtime +14 -exec rm {} \;
echo "✓ Rotated old backups (kept last 14 days)."
