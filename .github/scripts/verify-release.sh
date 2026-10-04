#!/usr/bin/env bash
set -euo pipefail
apk=${1:?APK path required}
: "${RELEASE_TAG:?Release tag required}"
tools="${ANDROID_HOME:?Android SDK required}/build-tools/35.0.0"
test -f "$apk" && test ! -L "$apk"
verify_dir=$(mktemp -d "${RUNNER_TEMP:?}/ford-verify.XXXXXX")
trap 'rm -rf -- "$verify_dir"' EXIT
"$tools/aapt" dump badging "$apk" > "$verify_dir/badging.txt"
python3 .github/scripts/release-metadata.py apk "$RELEASE_TAG" "$verify_dir/badging.txt"
"$tools/zipalign" -c -P 16 4 "$apk"
"$tools/apksigner" verify --verbose --print-certs "$apk" > "$verify_dir/signature.txt"
grep -Fxq 'Number of signers: 1' "$verify_dir/signature.txt"
grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$verify_dir/signature.txt"
grep -Fxq 'Verified using v3 scheme (APK Signature Scheme v3): true' "$verify_dir/signature.txt"
expected=$(tr -d '\r\n' < .github/release-certificate.sha256)
[[ "$expected" =~ ^[0-9a-f]{64}$ ]]
actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$verify_dir/signature.txt")
if [[ "$actual" != "$expected" ]]; then
  echo '::error::Signing certificate does not match the existing Ford Parts Desk release identity.'
  exit 1
fi
echo 'Release APK signature, certificate, alignment, application ID and version verified.'
