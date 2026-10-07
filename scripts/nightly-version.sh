#!/usr/bin/env bash
set -euo pipefail

BASE_VERSION="${1:-}"
if [ -z "$BASE_VERSION" ]; then
  echo "Usage: $0 <YY.MM>" >&2
  exit 1
fi

if [[ ! "$BASE_VERSION" =~ ^[0-9]{2}\.(0[1-9]|1[0-2])$ ]]; then
  echo "Base version must use CalVer YY.MM, for example 26.09." >&2
  exit 1
fi

TS="${PLEKTRA_NIGHTLY_TIMESTAMP:-$(date -u +'%Y%m%d.%H%M%S')}"
if [[ ! "$TS" =~ ^[0-9]{8}\.[0-9]{6}$ ]]; then
  echo "PLEKTRA_NIGHTLY_TIMESTAMP must use UTC YYYYMMDD.HHmmss." >&2
  exit 1
fi
if [ "${TS:2:2}.${TS:4:2}" != "$BASE_VERSION" ]; then
  echo "Timestamp $TS does not belong to CalVer month $BASE_VERSION." >&2
  exit 1
fi

NIGHTLY_SUFFIX="${TS:6:2}.${TS:9:6}Z"
FULL_VERSION="${BASE_VERSION}-nightly.${NIGHTLY_SUFFIX}"
TAG_NAME="nightly/v${FULL_VERSION}"

to_epoch_seconds() {
  local timestamp="$1"

  if date -u -d "1970-01-01 00:00:00" +%s >/dev/null 2>&1; then
    TZ=UTC date -d "${timestamp:0:8} ${timestamp:9:2}:${timestamp:11:2}:${timestamp:13:2}" +%s
  else
    TZ=UTC date -j -f '%Y%m%d.%H%M%S' "$timestamp" +%s
  fi
}

VERSION_CODE="$(to_epoch_seconds "$TS")"

printf 'timestamp=%s\n' "$TS"
printf 'nightly_suffix=%s\n' "$NIGHTLY_SUFFIX"
printf 'full_version=%s\n' "$FULL_VERSION"
printf 'tag_name=%s\n' "$TAG_NAME"
printf 'version_code=%s\n' "$VERSION_CODE"
