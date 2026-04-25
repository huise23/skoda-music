#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_DIR="${ROOT_DIR}/app/src/main/java"
APP_GRADLE="${ROOT_DIR}/app/build.gradle.kts"

failed=0

check_blocked_pattern() {
  local target="$1"
  local pattern="$2"
  local message="$3"
  local matches
  matches="$(grep -R -nE "${pattern}" "${target}" || true)"
  if [[ -n "${matches}" ]]; then
    echo "[api17-guard] ${message}"
    echo "${matches}"
    failed=1
  fi
}

check_blocked_pattern "${JAVA_DIR}" "import[[:space:]]+android\\.media\\.AudioAttributes" \
  "blocked framework API detected: android.media.AudioAttributes (API 21+)"

check_blocked_pattern "${JAVA_DIR}" "import[[:space:]]+android\\.media\\.session\\." \
  "blocked framework API detected: android.media.session.* (API 21+)"

check_blocked_pattern "${APP_GRADLE}" "androidx\\.media:media" \
  "blocked dependency detected: androidx.media:media (feature deferred for head-unit stability)"

if [[ "${failed}" -ne 0 ]]; then
  echo "[api17-guard] failed"
  exit 1
fi

echo "[api17-guard] passed"
