#!/usr/bin/env bash
set -euo pipefail
: "${RELEASE_TAG:?}" "${GH_TOKEN:?}" "${GITHUB_REPOSITORY:?}"
publish_dir=$(mktemp -d "${RUNNER_TEMP:?}/ford-publish.XXXXXX")
trap 'rm -rf -- "$publish_dir"' EXIT
# Recheck remote tag and release ordering just before writing anything.
git fetch origin "refs/tags/$RELEASE_TAG"
test "$(git rev-parse HEAD)" = "$(git rev-parse 'FETCH_HEAD^{commit}')"
gh api --paginate --slurp "repos/$GITHUB_REPOSITORY/releases?per_page=100" > "$publish_dir/releases.json"
python3 .github/scripts/release-metadata.py source "$RELEASE_TAG" "$publish_dir/releases.json"
checksum=$(cut -d ' ' -f 1 release-output/SHA256SUMS.txt)
certificate=$(cat .github/release-certificate.sha256)
cat > "$publish_dir/notes.md" <<EOF
Download **Ford-Parts-Desk.apk** for Android 8.0 or newer.

Built and tested from commit $(git rev-parse HEAD).
The release APK passed signature, certificate identity, version and alignment checks.
Verify the download with: \`sha256sum --check SHA256SUMS.txt\`.

APK SHA-256: \`$checksum\`
Signing certificate SHA-256: \`$certificate\`

Build and test run: https://github.com/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID

Unsigned/debug Actions artifacts are for development only. Updates require the same
signing identity and a higher versionCode. The offline catalog is a reference, not
a VIN-fitment guarantee; live authenticated Snap-on operation still needs device checks.
EOF
# Never clobber an existing release or asset. Failures leave an unpublished draft.
gh release create "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" --verify-tag --draft \
  --title "Ford Parts Desk ${RELEASE_TAG#v}" --notes-file "$publish_dir/notes.md" \
  release-output/Ford-Parts-Desk.apk release-output/SHA256SUMS.txt
gh release download "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" \
  --pattern Ford-Parts-Desk.apk --pattern SHA256SUMS.txt --dir "$publish_dir/download"
cmp release-output/Ford-Parts-Desk.apk "$publish_dir/download/Ford-Parts-Desk.apk"
cmp release-output/SHA256SUMS.txt "$publish_dir/download/SHA256SUMS.txt"
(cd "$publish_dir/download" && sha256sum --check --strict SHA256SUMS.txt)
gh release edit "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" --draft=false --latest
