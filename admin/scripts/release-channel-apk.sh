#!/usr/bin/env bash
set -euo pipefail

JOB_ID="${JOB_ID:-}"
CHANNEL_CODE="${CHANNEL_CODE:-}"
APP_APPLICATION_ID="${APP_APPLICATION_ID:-}"
ENGINE_APPLICATION_ID="${ENGINE_APPLICATION_ID:-}"
ENGINE_DATA_ROOT_NAME="${ENGINE_DATA_ROOT_NAME:-}"
API_BASE_URL="${API_BASE_URL:-}"
APP_NAME="${APP_NAME:-}"
ENGINE_NAME="${ENGINE_NAME:-}"
SOURCE_RELEASE_BRANCH="${SOURCE_RELEASE_BRANCH:-}"
CHANNEL_RELEASE_BRANCH="${CHANNEL_RELEASE_BRANCH:-}"
APP_VERSION_NAME="${APP_VERSION_NAME:-}"
APP_VERSION_CODE="${APP_VERSION_CODE:-}"
ENGINE_VERSION_NAME="${ENGINE_VERSION_NAME:-}"
ENGINE_VERSION_CODE="${ENGINE_VERSION_CODE:-}"
REPO_URL="${REPO_URL:-}"
BACKEND_BASE_URL="${BACKEND_BASE_URL:-}"
CALLBACK_URL="${CALLBACK_URL:-}"
PROGRESS_CALLBACK_URL="${PROGRESS_CALLBACK_URL:-}"
COMPLETE_CALLBACK_URL="${COMPLETE_CALLBACK_URL:-}"
JOB_TOKEN="${JOB_TOKEN:-}"
ADMIN_AUTHORIZATION_TOKEN="${ADMIN_AUTHORIZATION_TOKEN:-}"
DRY_RUN="${DRY_RUN:-false}"
PUSH_RELEASE_BRANCH="${PUSH_RELEASE_BRANCH:-true}"

UPLOAD_APP_URL="${UPLOAD_APP_URL:-}"
UPLOAD_ENGINE_URL="${UPLOAD_ENGINE_URL:-}"
LOCK_DIR="${LOCK_DIR:-/tmp/duodian-release-channel-apk-locks}"
WORK_ROOT="${WORK_ROOT:-/tmp/duodian-release-channel-apk}"
REPO_DIR=""
WORK_DIR=""
LOCK_FILE=""
LOCK_HELD=false

APP_ARTIFACT_PATH=""
ENGINE_ARTIFACT_PATH=""
APP_ARTIFACT_URL=""
ENGINE_ARTIFACT_URL=""
APP_MD5=""
APP_SHA256=""
APP_SIZE="0"
ENGINE_MD5=""
ENGINE_SHA256=""
ENGINE_SIZE="0"

usage() {
  cat <<'USAGE'
Usage:
  release-channel-apk.sh [options]

Options may also be supplied as environment variables:
  --job-id VALUE                     JOB_ID
  --channel-code VALUE               CHANNEL_CODE
  --app-application-id VALUE         APP_APPLICATION_ID
  --engine-application-id VALUE      ENGINE_APPLICATION_ID
  --engine-data-root-name VALUE      ENGINE_DATA_ROOT_NAME (defaults to blackbox for main, blackbox-<channel> otherwise)
  --api-base-url VALUE               API_BASE_URL passed to Android as DUODIAN_API_BASE_URL
  --app-name VALUE                   APP_NAME
  --engine-name VALUE                ENGINE_NAME
  --source-release-branch VALUE      SOURCE_RELEASE_BRANCH
  --channel-release-branch VALUE     CHANNEL_RELEASE_BRANCH
  --app-version-name VALUE           APP_VERSION_NAME
  --app-version-code VALUE           APP_VERSION_CODE
  --engine-version-name VALUE        ENGINE_VERSION_NAME
  --engine-version-code VALUE        ENGINE_VERSION_CODE
  --repo-url VALUE                   REPO_URL
  --backend-base-url VALUE           BACKEND_BASE_URL
  --callback-url VALUE               CALLBACK_URL (legacy, used for both callbacks)
  --progress-callback-url VALUE      PROGRESS_CALLBACK_URL
  --complete-callback-url VALUE      COMPLETE_CALLBACK_URL
  --job-token VALUE                  JOB_TOKEN
  --admin-authorization-token VALUE  ADMIN_AUTHORIZATION_TOKEN
  --dry-run                          DRY_RUN=true
  --no-push                          PUSH_RELEASE_BRANCH=false

Optional overrides:
  --upload-app-url VALUE             UPLOAD_APP_URL
  --upload-engine-url VALUE          UPLOAD_ENGINE_URL
  --lock-dir VALUE                   LOCK_DIR
  --work-root VALUE                  WORK_ROOT

Dry-run validates input, creates and removes a unique temp directory, acquires the lock,
skips clone/build, writes tiny simulated APK artifacts, then uploads and callbacks so
the admin release-job lifecycle can be verified locally.
USAGE
}

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*" >&2
}

fail() {
  printf '[%s] ERROR: %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*" >&2
  exit 1
}

is_true() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|y|Y|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "$value"
}

mask_token() {
  local token="$1"
  local length="${#token}"
  if [ "$length" -eq 0 ]; then
    printf '<empty>'
  elif [ "$length" -le 8 ]; then
    printf '***'
  else
    printf '%s***%s' "${token:0:4}" "${token: -4}"
  fi
}

mask_authorization() {
  local token="$1"
  if [ -z "$token" ]; then
    printf '<empty>'
  else
    printf 'Bearer %s' "$(mask_token "$token")"
  fi
}

safe_lock_name() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9_.-' '_'
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command: $1"
  fi
}

require_any_cmd() {
  local label="$1"
  shift
  local candidate
  for candidate in "$@"; do
    if command -v "$candidate" >/dev/null 2>&1; then
      return
    fi
  done
  fail "Missing required command for ${label}: one of $*"
}

find_aapt() {
  if command -v aapt >/dev/null 2>&1; then
    command -v aapt
    return
  fi
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
    find "$ANDROID_HOME/build-tools" -type f -name aapt | sort -V | tail -n 1
    return
  fi
  if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT/build-tools" ]; then
    find "$ANDROID_SDK_ROOT/build-tools" -type f -name aapt | sort -V | tail -n 1
    return
  fi
}

normalize_base_url() {
  local url="$1"
  printf '%s' "${url%/}"
}

api_url() {
  local path="$1"
  printf '%s/%s' "$(normalize_base_url "$BACKEND_BASE_URL")" "${path#/}"
}

parse_args() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --job-id) JOB_ID="${2:?Missing value for --job-id}"; shift 2 ;;
      --channel-code) CHANNEL_CODE="${2:?Missing value for --channel-code}"; shift 2 ;;
      --app-application-id) APP_APPLICATION_ID="${2:?Missing value for --app-application-id}"; shift 2 ;;
      --engine-application-id) ENGINE_APPLICATION_ID="${2:?Missing value for --engine-application-id}"; shift 2 ;;
      --engine-data-root-name) ENGINE_DATA_ROOT_NAME="${2:?Missing value for --engine-data-root-name}"; shift 2 ;;
      --api-base-url) API_BASE_URL="${2:?Missing value for --api-base-url}"; shift 2 ;;
      --app-name) APP_NAME="${2:?Missing value for --app-name}"; shift 2 ;;
      --engine-name) ENGINE_NAME="${2:?Missing value for --engine-name}"; shift 2 ;;
      --source-release-branch) SOURCE_RELEASE_BRANCH="${2:?Missing value for --source-release-branch}"; shift 2 ;;
      --channel-release-branch) CHANNEL_RELEASE_BRANCH="${2:?Missing value for --channel-release-branch}"; shift 2 ;;
      --app-version-name) APP_VERSION_NAME="${2:?Missing value for --app-version-name}"; shift 2 ;;
      --app-version-code) APP_VERSION_CODE="${2:?Missing value for --app-version-code}"; shift 2 ;;
      --engine-version-name) ENGINE_VERSION_NAME="${2:?Missing value for --engine-version-name}"; shift 2 ;;
      --engine-version-code) ENGINE_VERSION_CODE="${2:?Missing value for --engine-version-code}"; shift 2 ;;
      --repo-url) REPO_URL="${2:?Missing value for --repo-url}"; shift 2 ;;
      --backend-base-url) BACKEND_BASE_URL="${2:?Missing value for --backend-base-url}"; shift 2 ;;
      --callback-url) CALLBACK_URL="${2:?Missing value for --callback-url}"; shift 2 ;;
      --progress-callback-url) PROGRESS_CALLBACK_URL="${2:?Missing value for --progress-callback-url}"; shift 2 ;;
      --complete-callback-url) COMPLETE_CALLBACK_URL="${2:?Missing value for --complete-callback-url}"; shift 2 ;;
      --job-token) JOB_TOKEN="${2:?Missing value for --job-token}"; shift 2 ;;
      --admin-authorization-token) ADMIN_AUTHORIZATION_TOKEN="${2:?Missing value for --admin-authorization-token}"; shift 2 ;;
      --dry-run) DRY_RUN=true; shift ;;
      --no-push) PUSH_RELEASE_BRANCH=false; shift ;;
      --upload-app-url) UPLOAD_APP_URL="${2:?Missing value for --upload-app-url}"; shift 2 ;;
      --upload-engine-url) UPLOAD_ENGINE_URL="${2:?Missing value for --upload-engine-url}"; shift 2 ;;
      --lock-dir) LOCK_DIR="${2:?Missing value for --lock-dir}"; shift 2 ;;
      --work-root) WORK_ROOT="${2:?Missing value for --work-root}"; shift 2 ;;
      -h|--help) usage; exit 0 ;;
      *) fail "Unknown argument: $1" ;;
    esac
  done
}

validate_non_empty() {
  local name="$1"
  local value="$2"
  if [ -z "$value" ]; then
    fail "$name is required"
  fi
}

validate_package_name() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]]; then
    fail "$name must be a valid Android applicationId: $value"
  fi
}

validate_branch_name() {
  local name="$1"
  local value="$2"
  if [[ "$value" == -* ]] || [[ "$value" == *".."* ]] || [[ "$value" == *" "* ]] || [[ "$value" == *"~"* ]] || [[ "$value" == *"^"* ]] || [[ "$value" == *":"* ]] || [[ "$value" == *"?"* ]] || [[ "$value" == *"["* ]] || [[ "$value" == *"\\"* ]]; then
    fail "$name contains unsafe branch characters: $value"
  fi
}

default_engine_data_root_name() {
  if [ "$CHANNEL_CODE" = "main" ]; then
    printf 'blackbox'
    return
  fi
  printf 'blackbox-%s' "$(printf '%s' "$CHANNEL_CODE" | tr -c 'A-Za-z0-9_-' '-')"
}

validate_engine_data_root_name() {
  local value="$1"
  if [ -z "$value" ] || [[ "$value" == *"/"* ]] || [ "$value" = "." ] || [ "$value" = ".." ]; then
    fail "ENGINE_DATA_ROOT_NAME must be a safe directory name: $value"
  fi
}

validate_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || [ "$value" -le 0 ]; then
    fail "$name must be a positive integer: $value"
  fi
}

validate_inputs() {
  validate_non_empty JOB_ID "$JOB_ID"
  validate_non_empty CHANNEL_CODE "$CHANNEL_CODE"
  validate_non_empty APP_APPLICATION_ID "$APP_APPLICATION_ID"
  validate_non_empty ENGINE_APPLICATION_ID "$ENGINE_APPLICATION_ID"
  validate_non_empty APP_NAME "$APP_NAME"
  validate_non_empty ENGINE_NAME "$ENGINE_NAME"
  validate_non_empty SOURCE_RELEASE_BRANCH "$SOURCE_RELEASE_BRANCH"
  validate_non_empty CHANNEL_RELEASE_BRANCH "$CHANNEL_RELEASE_BRANCH"
  validate_non_empty APP_VERSION_NAME "$APP_VERSION_NAME"
  validate_non_empty APP_VERSION_CODE "$APP_VERSION_CODE"
  validate_non_empty ENGINE_VERSION_NAME "$ENGINE_VERSION_NAME"
  validate_non_empty ENGINE_VERSION_CODE "$ENGINE_VERSION_CODE"
  validate_non_empty REPO_URL "$REPO_URL"
  validate_non_empty BACKEND_BASE_URL "$BACKEND_BASE_URL"
  if [ -z "$PROGRESS_CALLBACK_URL" ] && [ -n "$CALLBACK_URL" ]; then
    PROGRESS_CALLBACK_URL="$CALLBACK_URL"
  fi
  if [ -z "$COMPLETE_CALLBACK_URL" ] && [ -n "$CALLBACK_URL" ]; then
    COMPLETE_CALLBACK_URL="$CALLBACK_URL"
  fi
  validate_non_empty PROGRESS_CALLBACK_URL "$PROGRESS_CALLBACK_URL"
  validate_non_empty COMPLETE_CALLBACK_URL "$COMPLETE_CALLBACK_URL"
  validate_non_empty JOB_TOKEN "$JOB_TOKEN"
  validate_non_empty ADMIN_AUTHORIZATION_TOKEN "$ADMIN_AUTHORIZATION_TOKEN"

  validate_package_name APP_APPLICATION_ID "$APP_APPLICATION_ID"
  validate_package_name ENGINE_APPLICATION_ID "$ENGINE_APPLICATION_ID"
  if [ -z "$ENGINE_DATA_ROOT_NAME" ]; then
    ENGINE_DATA_ROOT_NAME="$(default_engine_data_root_name)"
  fi
  if [ -z "$API_BASE_URL" ]; then
    API_BASE_URL="http://dpgj.zrnh.cn/api/"
  fi
  validate_engine_data_root_name "$ENGINE_DATA_ROOT_NAME"
  validate_branch_name SOURCE_RELEASE_BRANCH "$SOURCE_RELEASE_BRANCH"
  validate_branch_name CHANNEL_RELEASE_BRANCH "$CHANNEL_RELEASE_BRANCH"
  validate_integer APP_VERSION_CODE "$APP_VERSION_CODE"
  validate_integer ENGINE_VERSION_CODE "$ENGINE_VERSION_CODE"

  if [[ ! "$CHANNEL_CODE" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$ ]]; then
    fail "CHANNEL_CODE contains unsupported characters: $CHANNEL_CODE"
  fi

  case "$BACKEND_BASE_URL" in
    http://*|https://*) ;;
    *) fail "BACKEND_BASE_URL must start with http:// or https://" ;;
  esac

  case "$API_BASE_URL" in
    http://*|https://*) ;;
    *) fail "API_BASE_URL must start with http:// or https://" ;;
  esac

  case "$PROGRESS_CALLBACK_URL" in
    http://*|https://*) ;;
    *) fail "PROGRESS_CALLBACK_URL must start with http:// or https://" ;;
  esac

  case "$COMPLETE_CALLBACK_URL" in
    http://*|https://*) ;;
    *) fail "COMPLETE_CALLBACK_URL must start with http:// or https://" ;;
  esac

  if [ -z "$UPLOAD_APP_URL" ]; then
    UPLOAD_APP_URL="$(api_url "/files/apk")"
  fi
  if [ -z "$UPLOAD_ENGINE_URL" ]; then
    UPLOAD_ENGINE_URL="$(api_url "/files/engine-apk")"
  fi

  require_cmd mktemp
  require_cmd mkdir
  require_cmd rm
  require_cmd curl
  require_cmd stat
  require_any_cmd md5 md5sum md5
  require_any_cmd sha256 sha256sum shasum
  if ! is_true "$DRY_RUN"; then
    require_cmd git
    if [ -z "$(find_aapt)" ]; then
      fail "Missing required command: aapt (set ANDROID_HOME or ANDROID_SDK_ROOT)"
    fi
  fi

  log "Validated job ${JOB_ID} for channel ${CHANNEL_CODE}; engineDataRoot=${ENGINE_DATA_ROOT_NAME}; apiBaseUrl=${API_BASE_URL}; dryRun=${DRY_RUN}; token=$(mask_token "$JOB_TOKEN"); adminAuthorization=$(mask_authorization "$ADMIN_AUTHORIZATION_TOKEN")"
}

acquire_lock() {
  mkdir -p "$LOCK_DIR"
  LOCK_FILE="$LOCK_DIR/$(safe_lock_name "$CHANNEL_CODE").lock"
  if ! mkdir "$LOCK_FILE" 2>/dev/null; then
    fail "Release job already running for channel: ${CHANNEL_CODE}"
  fi
  LOCK_HELD=true
  printf '%s\n' "$JOB_ID" >"$LOCK_FILE/job-id"
  log "Acquired lock: $LOCK_FILE"
}

prepare_workspace() {
  mkdir -p "$WORK_ROOT"
  WORK_DIR="$(mktemp -d "${WORK_ROOT%/}/${CHANNEL_CODE}-${JOB_ID}.XXXXXX")"
  REPO_DIR="$WORK_DIR/repo"
  log "Prepared workspace: $WORK_DIR"

  if is_true "$DRY_RUN"; then
    log "[dry-run] Would clone repository into: $REPO_DIR"
    return
  fi

  git clone --quiet "$REPO_URL" "$REPO_DIR"
}

checkout_and_merge() {
  if is_true "$DRY_RUN"; then
    log "[dry-run] Would check out source branch ${SOURCE_RELEASE_BRANCH}, create/update ${CHANNEL_RELEASE_BRANCH}, merge source, and push=${PUSH_RELEASE_BRANCH}"
    return
  fi

  (
    cd "$REPO_DIR"
    git fetch --quiet origin "$SOURCE_RELEASE_BRANCH"
    git fetch --quiet origin "$CHANNEL_RELEASE_BRANCH" || true
    git checkout --quiet -B "$CHANNEL_RELEASE_BRANCH" "origin/$CHANNEL_RELEASE_BRANCH" 2>/dev/null \
      || git checkout --quiet -B "$CHANNEL_RELEASE_BRANCH" "origin/$SOURCE_RELEASE_BRANCH"
    git merge --no-edit "origin/$SOURCE_RELEASE_BRANCH"
    if is_true "$PUSH_RELEASE_BRANCH"; then
      git push --quiet origin "HEAD:${CHANNEL_RELEASE_BRANCH}"
    fi
  )
  if is_true "$PUSH_RELEASE_BRANCH"; then
    log "Merged ${SOURCE_RELEASE_BRANCH} into ${CHANNEL_RELEASE_BRANCH} and pushed branch"
  else
    log "Merged ${SOURCE_RELEASE_BRANCH} into ${CHANNEL_RELEASE_BRANCH}; push disabled"
  fi
}

verify_channel_config() {
  if is_true "$DRY_RUN"; then
    log "[dry-run] Would verify channel config: app=${APP_APPLICATION_ID}, engine=${ENGINE_APPLICATION_ID}, engineDataRoot=${ENGINE_DATA_ROOT_NAME}, apiBaseUrl=${API_BASE_URL}, appName=${APP_NAME}, engineName=${ENGINE_NAME}"
    return
  fi

  [ -f "$REPO_DIR/app/build.gradle" ] || fail "Missing app/build.gradle in checkout"
  [ -f "$REPO_DIR/Bcore/build.gradle" ] || fail "Missing Bcore/build.gradle in checkout"
  [ -x "$REPO_DIR/gradlew" ] || fail "Missing executable gradlew in checkout"

  log "Channel config inputs accepted for ${CHANNEL_CODE}; Gradle parameter support is verified by the build"
}

find_release_apk() {
  local search_dir="$1"
  local label="$2"
  local result_count
  local result

  result="$(find "$search_dir" -type f -name '*universal*.apk' -print -quit)"
  if [ -n "$result" ]; then
    printf '%s' "$result"
    return
  fi

  result_count="$(find "$search_dir" -type f -name '*.apk' | wc -l | tr -d ' ')"
  if [ "$result_count" -eq 0 ]; then
    fail "No ${label} APK found under $search_dir"
  fi
  if [ "$result_count" -gt 1 ]; then
    fail "Multiple ${label} APKs found under $search_dir and none is universal"
  fi
  result="$(find "$search_dir" -type f -name '*.apk' -print -quit)"
  printf '%s' "$result"
}

apk_badging_value() {
  local apk_path="$1"
  local key="$2"
  local aapt_bin
  aapt_bin="$(find_aapt)"
  "$aapt_bin" dump badging "$apk_path" | sed -n "s/.* ${key}='\\([^']*\\)'.*/\\1/p" | head -n 1
}

verify_apk_identity() {
  local apk_path="$1"
  local label="$2"
  local expected_package="$3"
  local expected_version_name="$4"
  local expected_version_code="$5"
  local actual_package
  local actual_version_name
  local actual_version_code

  if is_true "$DRY_RUN"; then
    log "[dry-run] Would verify ${label} APK manifest identity"
    return
  fi

  actual_package="$(apk_badging_value "$apk_path" "name")"
  actual_version_name="$(apk_badging_value "$apk_path" "versionName")"
  actual_version_code="$(apk_badging_value "$apk_path" "versionCode")"

  if [ "$actual_package" != "$expected_package" ]; then
    fail "${label} APK package mismatch: expected ${expected_package}, actual ${actual_package}"
  fi
  if [ "$actual_version_name" != "$expected_version_name" ]; then
    fail "${label} APK versionName mismatch: expected ${expected_version_name}, actual ${actual_version_name}"
  fi
  if [ "$actual_version_code" != "$expected_version_code" ]; then
    fail "${label} APK versionCode mismatch: expected ${expected_version_code}, actual ${actual_version_code}"
  fi
  log "Verified ${label} APK identity: package=${actual_package}, versionName=${actual_version_name}, versionCode=${actual_version_code}"
}

build_artifacts() {
  if is_true "$DRY_RUN"; then
    mkdir -p "$WORK_DIR/artifacts"
    APP_ARTIFACT_PATH="$WORK_DIR/artifacts/app-${CHANNEL_CODE}-${APP_VERSION_NAME}-${APP_VERSION_CODE}.apk"
    ENGINE_ARTIFACT_PATH="$WORK_DIR/artifacts/engine-${CHANNEL_CODE}-${ENGINE_VERSION_NAME}-${ENGINE_VERSION_CODE}.apk"
    printf 'dry-run app artifact job=%s channel=%s version=%s code=%s\n' "$JOB_ID" "$CHANNEL_CODE" "$APP_VERSION_NAME" "$APP_VERSION_CODE" >"$APP_ARTIFACT_PATH"
    printf 'dry-run engine artifact job=%s channel=%s version=%s code=%s\n' "$JOB_ID" "$CHANNEL_CODE" "$ENGINE_VERSION_NAME" "$ENGINE_VERSION_CODE" >"$ENGINE_ARTIFACT_PATH"
    log "[dry-run] Would build :app:assembleRelease and :Bcore:assembleRelease"
    log "[dry-run] Would pass Gradle properties: DUODIAN_APK_CHANNEL, DUODIAN_APP_APPLICATION_ID, DUODIAN_ENGINE_APPLICATION_ID, DUODIAN_ENGINE_DATA_ROOT_NAME, DUODIAN_API_BASE_URL, DUODIAN_APP_NAME, DUODIAN_ENGINE_NAME, DUODIAN_APP_VERSION_NAME/DUODIAN_APP_VERSION_CODE, DUODIAN_ENGINE_VERSION_NAME/DUODIAN_ENGINE_VERSION_CODE, APP_VERSION_NAME/APP_VERSION_CODE, ENGINE_VERSION_NAME/ENGINE_VERSION_CODE"
    return
  fi

  (
    cd "$REPO_DIR"
    ./gradlew \
      -PDUODIAN_APK_CHANNEL="$CHANNEL_CODE" \
      -PDUODIAN_APP_APPLICATION_ID="$APP_APPLICATION_ID" \
      -PDUODIAN_ENGINE_APPLICATION_ID="$ENGINE_APPLICATION_ID" \
      -PDUODIAN_ENGINE_DATA_ROOT_NAME="$ENGINE_DATA_ROOT_NAME" \
      -PDUODIAN_API_BASE_URL="$API_BASE_URL" \
      -PDUODIAN_APP_NAME="$APP_NAME" \
      -PDUODIAN_ENGINE_NAME="$ENGINE_NAME" \
      -PDUODIAN_APP_VERSION_NAME="$APP_VERSION_NAME" \
      -PDUODIAN_APP_VERSION_CODE="$APP_VERSION_CODE" \
      -PDUODIAN_ENGINE_VERSION_NAME="$ENGINE_VERSION_NAME" \
      -PDUODIAN_ENGINE_VERSION_CODE="$ENGINE_VERSION_CODE" \
      -PAPP_VERSION_NAME="$APP_VERSION_NAME" \
      -PAPP_VERSION_CODE="$APP_VERSION_CODE" \
      -PENGINE_VERSION_NAME="$ENGINE_VERSION_NAME" \
      -PENGINE_VERSION_CODE="$ENGINE_VERSION_CODE" \
      :app:assembleRelease \
      :Bcore:assembleRelease
  )

  APP_ARTIFACT_PATH="$(find_release_apk "$REPO_DIR/app/build/outputs/apk/release" "app")"
  ENGINE_ARTIFACT_PATH="$(find_release_apk "$REPO_DIR/Bcore/build/outputs/apk/release" "engine")"
  verify_apk_identity "$APP_ARTIFACT_PATH" "app" "$APP_APPLICATION_ID" "$APP_VERSION_NAME" "$APP_VERSION_CODE"
  verify_apk_identity "$ENGINE_ARTIFACT_PATH" "engine" "$ENGINE_APPLICATION_ID" "$ENGINE_VERSION_NAME" "$ENGINE_VERSION_CODE"
  log "Built app artifact: $APP_ARTIFACT_PATH"
  log "Built engine artifact: $ENGINE_ARTIFACT_PATH"
}

compute_file_md5() {
  if command -v md5sum >/dev/null 2>&1; then
    md5sum "$1" | awk '{print $1}'
  else
    md5 -q "$1"
  fi
}

compute_file_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

compute_file_size() {
  if stat -f '%z' "$1" >/dev/null 2>&1; then
    stat -f '%z' "$1"
  else
    stat -c '%s' "$1"
  fi
}

compute_checksums() {
  if is_true "$DRY_RUN"; then
    APP_MD5="$(compute_file_md5 "$APP_ARTIFACT_PATH")"
    APP_SHA256="$(compute_file_sha256 "$APP_ARTIFACT_PATH")"
    APP_SIZE="$(compute_file_size "$APP_ARTIFACT_PATH")"
    ENGINE_MD5="$(compute_file_md5 "$ENGINE_ARTIFACT_PATH")"
    ENGINE_SHA256="$(compute_file_sha256 "$ENGINE_ARTIFACT_PATH")"
    ENGINE_SIZE="$(compute_file_size "$ENGINE_ARTIFACT_PATH")"
    log "[dry-run] Computed checksums for simulated artifacts"
    return
  fi

  APP_MD5="$(compute_file_md5 "$APP_ARTIFACT_PATH")"
  APP_SHA256="$(compute_file_sha256 "$APP_ARTIFACT_PATH")"
  APP_SIZE="$(compute_file_size "$APP_ARTIFACT_PATH")"
  ENGINE_MD5="$(compute_file_md5 "$ENGINE_ARTIFACT_PATH")"
  ENGINE_SHA256="$(compute_file_sha256 "$ENGINE_ARTIFACT_PATH")"
  ENGINE_SIZE="$(compute_file_size "$ENGINE_ARTIFACT_PATH")"
  log "Computed checksums for app and engine artifacts"
}

extract_upload_url() {
  local response="$1"
  local extracted
  extracted="$(printf '%s' "$response" | sed -n 's/.*"url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
  if [ -z "$extracted" ]; then
    extracted="$(printf '%s' "$response" | sed -n 's/.*"data"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
  fi
  printf '%s' "$extracted"
}

upload_one_artifact() {
  local artifact_path="$1"
  local upload_url="$2"
  local label="$3"
  local response
  local artifact_url

  log "Uploading ${label} artifact to ${upload_url} with X-Apk-Channel=${CHANNEL_CODE} and adminAuthorization=$(mask_authorization "$ADMIN_AUTHORIZATION_TOKEN")"
  response="$(
    curl --fail --silent --show-error \
      -H "Authorization: Bearer ${ADMIN_AUTHORIZATION_TOKEN}" \
      -H "X-Apk-Channel: ${CHANNEL_CODE}" \
      -F "file=@${artifact_path}" \
      "$upload_url"
  )"
  artifact_url="$(extract_upload_url "$response")"
  if [ -z "$artifact_url" ]; then
    fail "Upload response for ${label} did not include a url field"
  fi
  printf '%s' "$artifact_url"
}

upload_artifacts() {
  APP_ARTIFACT_URL="$(upload_one_artifact "$APP_ARTIFACT_PATH" "$UPLOAD_APP_URL" "app")"
  ENGINE_ARTIFACT_URL="$(upload_one_artifact "$ENGINE_ARTIFACT_PATH" "$UPLOAD_ENGINE_URL" "engine")"
  log "Uploaded app and engine artifacts"
}

artifact_json() {
  local url="$1"
  local md5="$2"
  local sha256="$3"
  local size="$4"
  local version_name="$5"
  local version_code="$6"
  local application_id="$7"

  cat <<JSON
{"url":"$(json_escape "$url")","md5":"$(json_escape "$md5")","sha256":"$(json_escape "$sha256")","size":${size},"versionName":"$(json_escape "$version_name")","versionCode":${version_code},"applicationId":"$(json_escape "$application_id")"}
JSON
}

callback_payload() {
  local status="$1"
  local progress="$2"
  local message="$3"
  local success
  local error_message

  success=false
  error_message=""
  if [ "$status" = "SUCCESS" ]; then
    success=true
  elif [ "$status" = "FAILED" ]; then
    error_message="$message"
  fi

  cat <<JSON
{"jobId":"$(json_escape "$JOB_ID")","channelCode":"$(json_escape "$CHANNEL_CODE")","status":"$(json_escape "$status")","success":${success},"progress":${progress},"logExcerpt":"$(json_escape "$message")","errorMessage":"$(json_escape "$error_message")","appArtifactUrl":"$(json_escape "$APP_ARTIFACT_URL")","appArtifactMd5":"$(json_escape "$APP_MD5")","appArtifactSha256":"$(json_escape "$APP_SHA256")","appArtifactSize":${APP_SIZE},"appArtifactApplicationId":"$(json_escape "$APP_APPLICATION_ID")","engineArtifactUrl":"$(json_escape "$ENGINE_ARTIFACT_URL")","engineArtifactMd5":"$(json_escape "$ENGINE_MD5")","engineArtifactSha256":"$(json_escape "$ENGINE_SHA256")","engineArtifactSize":${ENGINE_SIZE},"engineArtifactApplicationId":"$(json_escape "$ENGINE_APPLICATION_ID")"}
JSON
}

post_callback() {
  local url="$1"
  local payload="$2"
  local response
  response="$(
    curl --fail --silent --show-error \
    -H "X-Release-Job-Token: ${JOB_TOKEN}" \
    -H "X-Apk-Channel: ${CHANNEL_CODE}" \
    -H "Content-Type: application/json" \
    --data "$payload" \
      "$url"
  )"
  if ! printf '%s' "$response" | grep -q '"code"[[:space:]]*:[[:space:]]*200'; then
    fail "Callback response was not successful: $response"
  fi
}

callback_progress() {
  local progress="$1"
  local message="$2"
  local payload

  payload="$(callback_payload "RUNNING" "$progress" "$message")"
  if is_true "$DRY_RUN"; then
    log "[dry-run] Posting progress callback progress=${progress} message=$(json_escape "$message")"
  fi

  log "Posting progress callback progress=${progress} with X-Apk-Channel=${CHANNEL_CODE} and token=$(mask_token "$JOB_TOKEN")"
  post_callback "$PROGRESS_CALLBACK_URL" "$payload"
}

callback_complete() {
  local status="$1"
  local progress="$2"
  local message="$3"
  local payload

  payload="$(callback_payload "$status" "$progress" "$message")"
  if is_true "$DRY_RUN"; then
    log "[dry-run] Posting complete callback status=${status} progress=${progress} message=$(json_escape "$message")"
  fi

  log "Posting completion callback status=${status} progress=${progress} with X-Apk-Channel=${CHANNEL_CODE} and token=$(mask_token "$JOB_TOKEN")"
  post_callback "$COMPLETE_CALLBACK_URL" "$payload"
}

cleanup() {
  local exit_code=$?
  if [ "$exit_code" -ne 0 ] && [ -n "$COMPLETE_CALLBACK_URL" ] && [ -n "$JOB_TOKEN" ] && [ -n "$CHANNEL_CODE" ]; then
    log "Release worker exiting with code ${exit_code}; posting failure callback"
    callback_complete "FAILED" 99 "release worker failed with exit code ${exit_code}" || true
  fi
  if [ -n "$WORK_DIR" ] && [ -d "$WORK_DIR" ]; then
    rm -rf "$WORK_DIR"
    log "Cleaned workspace: $WORK_DIR"
  fi
  if [ "$LOCK_HELD" = true ] && [ -n "$LOCK_FILE" ] && [ -d "$LOCK_FILE" ]; then
    rm -rf "$LOCK_FILE"
    log "Released lock: $LOCK_FILE"
  fi
  exit "$exit_code"
}

on_error() {
  local exit_code=$?
  trap - ERR
  if [ "$exit_code" -ne 0 ]; then
    log "Release worker failed with exit code ${exit_code}"
    if [ -n "$COMPLETE_CALLBACK_URL" ] && [ -n "$JOB_TOKEN" ] && [ -n "$CHANNEL_CODE" ]; then
      callback_complete "FAILED" 100 "release worker failed with exit code ${exit_code}" || true
    fi
  fi
  exit "$exit_code"
}

main() {
  parse_args "$@"
  validate_inputs
  acquire_lock
  prepare_workspace
  callback_progress 5 "workspace prepared"
  checkout_and_merge
  callback_progress 25 "release branches prepared"
  verify_channel_config
  callback_progress 35 "channel configuration verified"
  build_artifacts
  callback_progress 75 "artifacts built"
  compute_checksums
  callback_progress 85 "checksums computed"
  upload_artifacts
  callback_complete "SUCCESS" 100 "release worker completed"
}

trap on_error ERR
trap cleanup EXIT
main "$@"
