#!/usr/bin/env bash
set -euo pipefail

ADMIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-}"
COMPOSE_ENV_ARGS=()

if [ -z "$ENV_FILE" ]; then
  if [ -f "$ADMIN_DIR/.env.product" ]; then
    ENV_FILE=".env.product"
  elif [ -f "$ADMIN_DIR/.env" ]; then
    ENV_FILE=".env"
  fi
fi

if [ -n "$ENV_FILE" ]; then
  if [ ! -f "$ADMIN_DIR/$ENV_FILE" ]; then
    echo "ENV_FILE not found: $ADMIN_DIR/$ENV_FILE" >&2
    exit 1
  fi
  COMPOSE_ENV_ARGS=(--env-file "$ENV_FILE")
  set -a
  # shellcheck disable=SC1090
  source "$ADMIN_DIR/$ENV_FILE"
  set +a
fi

export DUODIAN_DATA_DIR="${DUODIAN_DATA_DIR:-$HOME/zhanghaoguanjia}"
SKIP_ADMIN_BUILD="${SKIP_ADMIN_BUILD:-0}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd docker
if [ "$SKIP_ADMIN_BUILD" != "1" ]; then
  require_cmd mvn
  require_cmd npm
  require_cmd java
fi

java_major_version() {
  java -version 2>&1 \
    | sed -n 's/.*version "\([^"]*\)".*/\1/p' \
    | cut -d. -f1 \
    | sed 's/^1$/8/'
}

use_java_home() {
  local candidate="$1"
  if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
}

if [ "$SKIP_ADMIN_BUILD" != "1" ] && [ "$(java_major_version)" -lt 21 ]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    use_java_home "$(/usr/libexec/java_home -v 21)"
  else
    for candidate in \
      /usr/lib/jvm/java-21-openjdk-amd64 \
      /usr/lib/jvm/java-1.21.0-openjdk-amd64 \
      /usr/lib/jvm/openjdk-21; do
      use_java_home "$candidate"
      if [ "$(java_major_version)" -ge 21 ]; then
        break
      fi
    done
  fi
fi

if [ "$SKIP_ADMIN_BUILD" != "1" ] && [ "$(java_major_version)" -lt 21 ]; then
  echo "JDK 21 or newer is required to build admin backend. Current java:" >&2
  java -version >&2
  exit 1
fi

mkdir -p "$DUODIAN_DATA_DIR/logs" "$DUODIAN_DATA_DIR/files" "$DUODIAN_DATA_DIR/db_backup"

if [ "$SKIP_ADMIN_BUILD" = "1" ]; then
  if [ ! -f "$ADMIN_DIR/backend/target/admin-backend-1.0.0.jar" ]; then
    echo "Missing prebuilt backend jar: $ADMIN_DIR/backend/target/admin-backend-1.0.0.jar" >&2
    exit 1
  fi
  if [ ! -f "$ADMIN_DIR/frontend/dist/index.html" ]; then
    echo "Missing prebuilt frontend dist: $ADMIN_DIR/frontend/dist/index.html" >&2
    exit 1
  fi
  echo "Using prebuilt admin backend jar and frontend dist."
else
  echo "Building admin backend jar..."
  echo "Using Java: $(java -version 2>&1 | head -n 1)"
  (cd "$ADMIN_DIR/backend" && mvn clean package -DskipTests)

  echo "Building admin frontend dist..."
  (cd "$ADMIN_DIR/frontend" && npm ci && npm run build)
fi

echo "Starting admin services on port ${DUODIAN_FRONTEND_PORT:-8006}..."
if [ "${#COMPOSE_ENV_ARGS[@]}" -gt 0 ]; then
  (cd "$ADMIN_DIR" && docker compose "${COMPOSE_ENV_ARGS[@]}" up -d --build)
else
  (cd "$ADMIN_DIR" && docker compose up -d --build)
fi

echo "Admin is available at http://localhost:${DUODIAN_FRONTEND_PORT:-8006}"
