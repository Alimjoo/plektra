#!/usr/bin/env bash

set -euo pipefail

usage() {
  printf 'Usage: %s <base-version> <output-apk> [--variant full|no-accessibility] [--timestamp YYYYMMDD.HHMMSS] [--fdroid] [--skip-unit-tests]\n' "$0" >&2
  exit 2
}

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

[[ $# -ge 2 ]] || usage
base_version=$1
output_apk=$2
shift 2

timestamp=
fdroid=false
skip_unit_tests=false
variant=full
while [[ $# -gt 0 ]]; do
  case "$1" in
    --variant)
      [[ $# -ge 2 ]] || usage
      variant=$2
      shift 2
      ;;
    --timestamp)
      [[ $# -ge 2 ]] || usage
      timestamp=$2
      shift 2
      ;;
    --fdroid)
      fdroid=true
      shift
      ;;
    --skip-unit-tests)
      skip_unit_tests=true
      shift
      ;;
    *) usage ;;
  esac
done

case "$variant" in
  full) flavor=nightly; task_flavor=Nightly ;;
  no-accessibility) flavor=nightlyNoAccessibility; task_flavor=NightlyNoAccessibility ;;
  *) fail "unknown APK variant: $variant" ;;
esac

if [[ -z "$timestamp" ]]; then
  timestamp=$(date -u +'%Y%m%d.%H%M%S')
fi
[[ "$timestamp" =~ ^[0-9]{8}\.[0-9]{6}$ ]] || fail "invalid timestamp: $timestamp"

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
apksigner="$sdk_root/build-tools/36.0.0/apksigner"
apkanalyzer="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
gradle_apk="$root_dir/app/build/outputs/apk/$flavor/release/app-$flavor-release-unsigned.apk"

[[ -x "$apksigner" && -x "$apkanalyzer" ]] || fail "required Android SDK tools are unavailable"
mkdir -p "$(dirname -- "$output_apk")"
[[ ! -e "$output_apk" ]] || fail "output already exists: $output_apk"

version_info=$(PLEKTRA_NIGHTLY_TIMESTAMP="$timestamp" "$root_dir/scripts/nightly-version.sh" "$base_version")
nightly_suffix=$(printf '%s\n' "$version_info" | awk -F= '/^nightly_suffix=/{print $2}')
version_code=$(printf '%s\n' "$version_info" | awk -F= '/^version_code=/{print $2}')
full_version=$(printf '%s\n' "$version_info" | awk -F= '/^full_version=/{print $2}')
gradle_args=(
  -PPLEKTRA_UNSIGNED_RELEASE_BUILD=true
  -PPLEKTRA_VERSION_NAME="$base_version"
  -PPLEKTRA_NIGHTLY_VERSION_CODE="$version_code"
  -PPLEKTRA_NIGHTLY_VERSION_SUFFIX="-nightly.${nightly_suffix}"
)
if [[ "$fdroid" == true ]]; then
  gradle_args+=(-PPLEKTRA_FDROID_BUILD=true)
fi

cd "$root_dir"
if [[ "$skip_unit_tests" == false ]]; then
  ./gradlew ":app:test${task_flavor}ReleaseUnitTest" "${gradle_args[@]}"
else
  printf 'Skipping unit tests; use only after the relevant checks have passed.\n'
fi
./gradlew ":app:assemble${task_flavor}Release" "${gradle_args[@]}"

[[ -f "$gradle_apk" ]] || fail "Gradle did not create the expected unsigned APK: $gradle_apk"
if "$apksigner" verify "$gradle_apk" >/dev/null 2>&1; then
  fail "Gradle output is already signed"
fi

actual_app_id=$("$apkanalyzer" manifest application-id "$gradle_apk")
actual_version_code=$("$apkanalyzer" manifest version-code "$gradle_apk")
actual_version_name=$("$apkanalyzer" manifest version-name "$gradle_apk")
actual_debuggable=$("$apkanalyzer" manifest debuggable "$gradle_apk")
[[ "$actual_app_id" == "rocks.pkb.plektra.nightly" ]] || fail "unexpected application ID: $actual_app_id"
[[ "$actual_version_code" == "$version_code" ]] || fail "unexpected version code: $actual_version_code"
[[ "$actual_version_name" == "$full_version" ]] || fail "unexpected version name: $actual_version_name"
[[ "$actual_debuggable" == "false" ]] || fail "release APK is debuggable"

manifest=$("$apkanalyzer" manifest print "$gradle_apk")
service_present=false
if [[ "$manifest" == *'android.permission.BIND_ACCESSIBILITY_SERVICE'* ||
      "$manifest" == *'android.accessibilityservice.AccessibilityService'* ]]; then
  service_present=true
fi
if [[ "$variant" == "no-accessibility" && "$service_present" == true ]]; then
  fail "no-accessibility APK still declares an accessibility service"
fi
if [[ "$variant" == "full" && "$service_present" == false ]]; then
  fail "full APK is missing its accessibility service"
fi

cp -- "$gradle_apk" "$output_apk"
shasum -a 256 "$output_apk" >"${output_apk}.sha256"

printf 'Success: unsigned Plektra Nightly release built and verified.\n'
printf 'delivery=%s\n' "$([[ "$fdroid" == true ]] && printf fdroid || printf github)"
printf 'application_id=%s\n' "$actual_app_id"
printf 'apk_variant=%s\n' "$variant"
printf 'version_name=%s\n' "$actual_version_name"
printf 'version_code=%s\n' "$actual_version_code"
printf 'apk=%s\n' "$output_apk"
printf 'sha256=%s\n' "${output_apk}.sha256"
