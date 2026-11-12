#!/usr/bin/env bash
# Daily backup of every IT-ILMS database (itilms_*) from the Postgres container.
#
#   deploy/scripts/backup-databases.sh                 # a dump of each database into ~/backups/YYYY-MM-DD/
#   BACKUP_DIR=/mnt/disk/backups deploy/scripts/backup-databases.sh
#   KEEP_DAILY=14 KEEP_MONTHLY=12                      # how long to keep them (defaults shown)
#   OFFSITE_RCLONE_REMOTE=gdrive:itilms-backups        # optional: also copy to another place with rclone
#
# Each dump is checked (gzip test, not empty) and the script exits non-zero if any database failed, so a cron mail or
# a monitor can notice. Dumps of the first day of a month are kept for KEEP_MONTHLY months; the rest for KEEP_DAILY days.
# To restore one database:  gunzip -c itilms_finance.sql.gz | docker exec -i itilms-postgres-1 psql -U <user> -d itilms_finance
# (create it empty first: docker exec itilms-postgres-1 psql -U <user> -c 'CREATE DATABASE itilms_finance').

set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${ENV_FILE:-$HERE/../docker/.env}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAILY="${KEEP_DAILY:-14}"
KEEP_MONTHLY="${KEEP_MONTHLY:-12}"
CONTAINER="${PG_CONTAINER:-$(docker ps -qf name=postgres | head -n 1)}"

if [ -z "$CONTAINER" ]; then
  echo "$(date -Is) backup: the Postgres container is not running" >&2
  exit 2
fi
PG_USER="${POSTGRES_USER:-$(grep -E '^POSTGRES_USER=' "$ENV_FILE" | cut -d= -f2-)}"
if [ -z "$PG_USER" ]; then
  echo "$(date -Is) backup: POSTGRES_USER is not set (looked in $ENV_FILE)" >&2
  exit 2
fi

DAY="$(date +%F)"
TARGET="$BACKUP_DIR/$DAY"
mkdir -p "$TARGET"
failed=0
count=0

databases="$(docker exec "$CONTAINER" psql -U "$PG_USER" -d postgres -Atc "SELECT datname FROM pg_database WHERE datname LIKE 'itilms\_%' ORDER BY 1")"
for db in $databases; do
  out="$TARGET/$db.sql.gz"
  tmp="$out.partial"
  if docker exec "$CONTAINER" pg_dump -U "$PG_USER" --no-owner "$db" | gzip -9 > "$tmp" \
      && gzip -t "$tmp" && [ "$(stat -c %s "$tmp")" -gt 200 ]; then
    mv "$tmp" "$out"
    count=$((count + 1))
  else
    echo "$(date -Is) backup: $db FAILED" >&2
    rm -f "$tmp"
    failed=$((failed + 1))
  fi
done

# The first of the month is kept longer; everything else is thinned out.
find "$BACKUP_DIR" -maxdepth 1 -type d -name '20??-??-??' | while read -r dir; do
  name="$(basename "$dir")"
  case "$name" in
    *-01) limit=$((KEEP_MONTHLY * 31)) ;;
    *)    limit=$KEEP_DAILY ;;
  esac
  if [ -n "$(find "$dir" -maxdepth 0 -mtime +"$limit")" ]; then
    rm -rf "$dir"
  fi
done

if [ -n "${OFFSITE_RCLONE_REMOTE:-}" ]; then
  if command -v rclone >/dev/null 2>&1; then
    rclone copy "$TARGET" "$OFFSITE_RCLONE_REMOTE/$DAY" || { echo "$(date -Is) backup: offsite copy FAILED" >&2; failed=$((failed + 1)); }
  else
    echo "$(date -Is) backup: OFFSITE_RCLONE_REMOTE is set but rclone is not installed" >&2
    failed=$((failed + 1))
  fi
fi

echo "$(date -Is) backup: $count database(s) saved to $TARGET, $failed problem(s)" | tee -a "$BACKUP_DIR/backup.log"
[ "$failed" -eq 0 ]
