#!/usr/bin/env bash
set -euo pipefail

ADMIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-}"
COMPOSE_ENV_ARGS=()

if [ -n "$ENV_FILE" ]; then
  if [ ! -f "$ADMIN_DIR/$ENV_FILE" ]; then
    echo "ENV_FILE not found: $ADMIN_DIR/$ENV_FILE" >&2
    exit 1
  fi
  COMPOSE_ENV_ARGS=(--env-file "$ENV_FILE")
fi

export DUODIAN_DATA_DIR="${DUODIAN_DATA_DIR:-$HOME/dianpuguanjia}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd mvn
require_cmd npm
require_cmd docker
require_cmd java

java_major_version() {
  java -version 2>&1 \
    | sed -n 's/.*version "\([^"]*\)".*/\1/p' \
    | cut -d. -f1 \
    | sed 's/^1$/8/'
}

if [ "$(java_major_version)" -lt 21 ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ "$(java_major_version)" -lt 21 ]; then
  echo "JDK 21 or newer is required to build admin backend. Current java:" >&2
  java -version >&2
  exit 1
fi

mkdir -p "$DUODIAN_DATA_DIR/logs" "$DUODIAN_DATA_DIR/files" "$DUODIAN_DATA_DIR/db_backup"

echo "Building admin backend jar..."
echo "Using Java: $(java -version 2>&1 | head -n 1)"
(cd "$ADMIN_DIR/backend" && mvn clean package -DskipTests)

echo "Building admin frontend dist..."
(cd "$ADMIN_DIR/frontend" && npm ci && npm run build)

echo "Starting admin services on port ${DUODIAN_FRONTEND_PORT:-8006}..."
(cd "$ADMIN_DIR" && docker compose "${COMPOSE_ENV_ARGS[@]}" up -d --build)

echo "Admin is available at http://localhost:${DUODIAN_FRONTEND_PORT:-8006}"
