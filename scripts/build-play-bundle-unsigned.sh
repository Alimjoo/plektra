#!/usr/bin/env bash
set -euo pipefail
[[ $# -ge 3 && $# -le 4 ]] || { echo "Usage: $0 nightly|stable YY.MM OUTPUT.aab [UTC-timestamp]" >&2; exit 2; }
channel=$1
version=$2
output=$(python3 -c 'import os,sys; print(os.path.abspath(sys.argv[1]))' "$3")
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
[[ ! -e "$output" ]] || { echo "Output already exists" >&2; exit 1; }
[[ "$version" =~ ^[0-9]{2}\.(0[1-9]|1[0-2])$ ]] || exit 2
args=(-PPLEKTRA_UNSIGNED_RELEASE_BUILD=true -PPLEKTRA_VERSION_NAME="$version")
case "$channel" in
 nightly)
  info=$(PLEKTRA_NIGHTLY_TIMESTAMP="${4:-$(date -u +'%Y%m%d.%H%M%S')}" "$root_dir/scripts/nightly-version.sh" "$version")
  code=$(printf '%s\n' "$info" | awk -F= '/^version_code=/{print $2}')
  suffix=$(printf '%s\n' "$info" | awk -F= '/^nightly_suffix=/{print $2}')
  expected_version="$version-nightly.$suffix"
  args+=(-PPLEKTRA_NIGHTLY_VERSION_CODE="$code" -PPLEKTRA_NIGHTLY_VERSION_SUFFIX="-nightly.$suffix")
  flavor=playNightly
  task=PlayNightly
  ;;
 stable)
  [[ $# -eq 3 ]] || exit 2
  code=$((10#${version:0:2} * 1000000 + 10#${version:3:2} * 10000))
  expected_version=$version
  args+=(-PPLEKTRA_VERSION_CODE="$code")
  flavor=playStable
  task=PlayStable
  ;;
 *) exit 2;;
esac
java_home=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home}
bundletool="$HOME/.local/share/agent-tools/android/bundletool.jar"
[[ -f "$bundletool" ]] || { echo "Install Google's bundletool at $bundletool" >&2; exit 1; }
cd "$root_dir"
./gradlew ":app:bundle${task}Release" "${args[@]}"
bundle="$root_dir/app/build/outputs/bundle/${flavor}Release/app-${flavor}-release.aab"
[[ -f "$bundle" ]] || { echo "Bundle not found: $bundle" >&2; exit 1; }
manifest=$("$java_home/bin/java" -jar "$bundletool" dump manifest --bundle="$bundle")
expected_package=rocks.pkb.plektra.unsovereign
[[ "$channel" == nightly ]] && expected_package+=.nightly
printf '%s' "$manifest" | python3 -c 'import sys,xml.etree.ElementTree as E; r=E.fromstring(sys.stdin.read()); a="{http://schemas.android.com/apk/res/android}"; assert r.get("package")==sys.argv[1]; assert r.get(a+"versionCode")==sys.argv[2]; assert r.get(a+"versionName")==sys.argv[3]; assert r.find("application").get(a+"debuggable","false")=="false"' "$expected_package" "$code" "$expected_version"
python3 - "$bundle" "$expected_version" "$code" <<'PY_CHECK'
import json,sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    component=json.loads(z.read('base/assets/plektra-sbom.cdx.json'))['metadata']['component']
    assert component['version']==sys.argv[2], 'SBOM version mismatch'
    assert any(p['name']=='android:versionCode' and p['value']==sys.argv[3] for p in component['properties']), 'SBOM version-code mismatch'
    assert not any(n.upper().startswith('META-INF/') and n.upper().endswith(('.SF','.RSA','.EC','.DSA')) for n in z.namelist()), 'Bundle is already signed'
PY_CHECK
"$java_home/bin/java" -jar "$bundletool" validate --bundle="$bundle" > /dev/null
mkdir -p "$(dirname -- "$output")"
cp -n "$bundle" "$output"
shasum -a 256 "$output" >"$output.sha256"
printf 'package=%s\nversion=%s\ncode=%s\nbundle=%s\n' "$expected_package" "$expected_version" "$code" "$output"
