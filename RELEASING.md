# Android releases

The `Publish signed Android release` workflow runs only when a stable `vMAJOR.MINOR.PATCH`
tag is pushed. Branch pushes, pull requests and manual build checks never sign or publish.
It requires a commit already on `main`, matching source/APK versions, a new release tag,
successful catalog tests, release/debug lint, signing guard tests and Android emulator tests.

## Existing release identity

`v3.0.0` was published on October 4, 2026 from
`774f86afaca628e32f9c9ac3777a3bf37e1dc207`, with versionCode `30000`.
Its downloaded APK was independently checked with `apksigner verify` and SHA-256
while adding this workflow:

- APK SHA-256: `a03b69126ef3d183b03741335d8fae2551ae24f20346b6dae148fa8826454e91`
- Certificate SHA-256: `039c557c7b4ffee9f0789e16e54683d5f536508396cac9ed94622fba25be4800`

The public certificate fingerprint is pinned in `.github/release-certificate.sha256`.
It is not a private key or a secret. The workflow refuses a different signing certificate.
Do not generate a replacement key or edit this pin to work around missing credentials:
that would break in-place updates. Key rotation needs a separate Android signing-lineage plan.

## One-time owner setup

Create the GitHub environment **`android-release`** under repository **Settings →
Environments**. Restrict deployment branches/tags to release tags (`v*` tag rule, no
branch rule), and use a required reviewer if appropriate. Protect `main` and the `v*`
tag namespace with repository rules so only trusted maintainers can change release
code or create tags. Environment configuration and secrets are not created by workflow YAML.

Add these four **environment secrets**, each separately:

| Secret | Required value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Standard base64 of the existing JKS/PKCS12 keystore containing the v3.0.0 private signing key. Base64 is encoding, not encryption. |
| `ANDROID_KEYSTORE_PASSWORD` | Password opening that keystore. |
| `ANDROID_KEY_ALIAS` | Alias of that exact private-key entry. |
| `ANDROID_KEY_PASSWORD` | Password for the private-key entry, even if equal to the keystore password. |

Enter values directly in GitHub's secret settings or using a local secret-management
tool; do not put them in issues, chat, source files, Actions logs or release assets.
The signing job reports missing **names only** and stops. This task had no signing
credentials and no API for inspecting/configuring GitHub environment secrets; their
presence in GitHub has not been asserted. No custom publishing PAT is needed:
the publish job uses the automatic `GITHUB_TOKEN` with `contents: write`.

The key must match the pinned certificate. On the owner's trusted computer, inspect
the existing keystore with `keytool -list -v -keystore /private/path/release.jks`
(enter its password at the prompt) and compare the SHA-256 certificate fingerprint,
ignoring colons/case. Prepare base64 privately and keep an encrypted offline backup
of the keystore, alias and passwords. The v3.0.0 release notes say its separate private
backup was pending; confirm that backup before relying on automated releases.

## Cut a release after setup

1. Update `versionName` in `android/app/build.gradle` and `version` in `package.json`
   to the same stable version. Set `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`.
   Minor and patch components must be 0–99. The next example is `3.0.1` / `30001`;
   this change intentionally does not bump the application version.
2. Merge the reviewed changes to `main` and wait for build/test checks.
3. From an up-to-date checkout of `main`, create and push the new tag, for example:

   ```bash
   git tag -a v3.0.1 -m 'Ford Parts Desk 3.0.1'
   git push origin v3.0.1
   ```

4. Review/approve the `android-release` environment job if configured. The workflow
   rebuilds and tests the tagged commit; it never signs an APK from a previous run.
5. After successful publication, download both release assets and check:

   ```bash
   sha256sum --check SHA256SUMS.txt
   "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs Ford-Parts-Desk.apk
   ```

Compare the reported certificate SHA-256 with the pinned value above. A checksum
detects changed bytes; it does not replace signature/certificate verification.

## Security and failure behavior

- Every action in the release/build path is pinned to a full commit SHA.
- The reusable build job gets no signing secrets and has read-only repository access.
  It exercises signing with a disposable test key in a temporary test directory;
  that key/output is removed and never uploaded or used as the release identity.
- The signing job runs on a fresh hosted runner with read-only repository access.
  It downloads only this run's exact unsigned release artifact. It rejects already
  signed or debuggable APKs, wrong application IDs and mismatched versions.
- Gradle never sees the release key. Decoding uses a private temporary directory
  (`umask 077`), passwords use `apksigner` environment readers, signing diagnostics
  remain private, and exit/signal cleanup removes temporary key material. Hard runner
  termination relies on disposal of the hosted VM; no keystore is cached or uploaded.
- Alignment runs before signing. Signature schemes v2/v3, one expected signer,
  manifest metadata and alignment must verify before generating `SHA256SUMS.txt`.
  Only the two explicitly named public deliverables are transferred to the next job.
- The separate publish job has no signing secrets. It verifies the downloaded APK
  and exact checksum again, uploads to a draft, downloads and compares the uploaded
  bytes, then publishes. No unsigned/debug APK, keystore, private diagnostic, or
  complete working-directory archive is attached to a release.
- Existing tags/releases are not overwritten. The version must exceed every published
  stable release. Releases are serialized to avoid overlapping publication.

If credentials are missing or incorrect, fix the environment secrets and rerun the
failed job for the unchanged tag. Do not replace the tag or use a debug APK as a workaround.
If publication fails after creating a draft, it remains unpublished. Inspect that
draft and its assets; remove only the failed draft after confirming it was never
published, then rerun the failed publish job. The workflow never deletes or clobbers
release assets automatically. Once a release is public, correct it with a new version/tag.

Local validation: `python3 tests/release_metadata_test.py`, shell syntax checks and
`actionlint` validate the guards/workflow. After building the APK, run
`python3 tests/release_signing_test.py` with SDK build-tools 35.0.0 in `ANDROID_HOME`.
The integration tests use real `apksigner` to cover valid signing, checksum/tamper
detection, wrong credentials, wrong certificates, missing secrets and signed-input rejection.
