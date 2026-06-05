#!/bin/sh
set -eu

: "${MYSQL_HOST:=duodian-mysql}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USER:=root}"
: "${MYSQL_PASS:?MYSQL_PASS is required}"
: "${MYSQL_DB:=duodian_admin}"
: "${MYSQL_BACKUP_INTERVAL_SECONDS:=86400}"
: "${MYSQL_BACKUP_MAX_BACKUPS:=30}"
: "${MYSQL_BACKUP_INIT:=1}"
: "${BACKUP_DIR:=/backup}"

mkdir -p "$BACKUP_DIR"
export MYSQL_PWD="$MYSQL_PASS"

log() {
  echo "[$(date '+%Y-%m-%dT%H:%M:%S%z')] $*"
}

wait_for_mysql() {
  until mysqladmin ping \
    -h "$MYSQL_HOST" \
    -P "$MYSQL_PORT" \
    -u "$MYSQL_USER" \
    --silent >/dev/null 2>&1; do
    log "waiting for mysql at ${MYSQL_HOST}:${MYSQL_PORT}"
    sleep 5
  done
}

cleanup_old_backups() {
  if [ "$MYSQL_BACKUP_MAX_BACKUPS" -le 0 ]; then
    return
  fi

  files="$(ls -1t "$BACKUP_DIR"/"${MYSQL_DB}"_*.sql.gz 2>/dev/null || true)"
  count="$(printf '%s\n' "$files" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [ "$count" -le "$MYSQL_BACKUP_MAX_BACKUPS" ]; then
    return
  fi

  printf '%s\n' "$files" \
    | sed -n "$((MYSQL_BACKUP_MAX_BACKUPS + 1)),\$p" \
    | while IFS= read -r old_backup; do
        [ -n "$old_backup" ] && rm -f "$old_backup"
      done
}

backup_once() {
  timestamp="$(date '+%Y%m%d_%H%M%S')"
  target="$BACKUP_DIR/${MYSQL_DB}_${timestamp}.sql.gz"
  tmp="${target}.tmp"

  log "backing up ${MYSQL_DB} to ${target}"
  mysqldump \
    -h "$MYSQL_HOST" \
    -P "$MYSQL_PORT" \
    -u "$MYSQL_USER" \
    --single-transaction \
    --routines \
    --triggers \
    --default-character-set=utf8mb4 \
    "$MYSQL_DB" \
    | gzip > "$tmp"
  mv "$tmp" "$target"
  cleanup_old_backups
  log "backup completed: ${target}"
}

wait_for_mysql

if [ "$MYSQL_BACKUP_INIT" = "1" ]; then
  backup_once
fi

while true; do
  sleep "$MYSQL_BACKUP_INTERVAL_SECONDS"
  wait_for_mysql
  backup_once
done
