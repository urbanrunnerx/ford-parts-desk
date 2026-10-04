#!/usr/bin/env bash
# Secrets exist only for this step, never in Gradle, caches, artifacts or logs.
set +x
set -euo pipefail
umask 077
missing=()
for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [[ -z "${!name:-}" ]]; then missing+=("$name"); fi
done
if (( ${#missing[@]} )); then
  printf '::error::Missing android-release secret: %s\n' "${missing[@]}"
  exit 1
fi
: "${RELEASE_TAG:?Release tag required}"
tools="${ANDROID_HOME:?Android SDK required}/build-tools/35.0.0"
input=release-input/app-release-unsigned.apk
test -f "$input" && test ! -L "$input"
test "$(find release-input -mindepth 1 -maxdepth 1 | wc -l)" -eq 1
# This job accepts precisely the unsigned release output, never a signed/debug APK.
if "$tools/apksigner" verify "$input" >/dev/null 2>&1; then
  echo '::error::Refusing to re-sign an already signed APK.'
  exit 1
fi
private_dir=$(mktemp -d "${RUNNER_TEMP:?}/ford-signing.XXXXXX")
complete=false
cleanup() {
  rm -rf -- "$private_dir"
  if [[ "$complete" != true ]]; then rm -rf -- release-output; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"$tools/aapt" dump badging "$input" > "$private_dir/badging.txt"
python3 .github/scripts/release-metadata.py apk "$RELEASE_TAG" "$private_dir/badging.txt"
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$private_dir/release.jks"
unset ANDROID_KEYSTORE_BASE64
test -s "$private_dir/release.jks"
"$tools/zipalign" -P 16 -f 4 "$input" "$private_dir/aligned.apk"
mkdir release-output
# Password values are passed through env readers, never command-line literals.
# Suppress private tool diagnostics (bad aliases/keystores can echo input).
if ! "$tools/apksigner" sign \
  --ks "$private_dir/release.jks" --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD \
  --v1-signing-enabled false --v2-signing-enabled true \
  --v3-signing-enabled true --v4-signing-enabled false \
  --out release-output/Ford-Parts-Desk.apk "$private_dir/aligned.apk" \
  > "$private_dir/signing.log" 2>&1; then
  echo '::error::Signing failed. Check the keystore, alias and passwords in android-release secrets.'
  exit 1
fi
unset ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_PASSWORD ANDROID_KEY_ALIAS
rm -rf -- "$private_dir"
bash .github/scripts/verify-release.sh release-output/Ford-Parts-Desk.apk
(cd release-output && sha256sum Ford-Parts-Desk.apk > SHA256SUMS.txt)
complete=true
