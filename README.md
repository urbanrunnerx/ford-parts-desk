# Ford Parts Desk

An offline Ford base-number reference for a parts-department employee. Search a familiar name, shop synonym, exact base or full service part number. One family card contains its applicable base numbers.

[Project releases](https://github.com/urbanrunnerx/ford-parts-desk/releases) · [Build checks](https://github.com/urbanrunnerx/ford-parts-desk/actions)

The catalog and features below describe this source tree. Ford Parts Desk is a separately installed replacement. Download the verified signed APK from [Project releases](https://github.com/urbanrunnerx/ford-parts-desk/releases). Unsigned and debug workflow artifacts are for testing, not installation as a release.

## Included catalog

- **15,703 distinct exact base numbers**, grouped into 2,442 named families and broad reference groups.
- 15,172 bases from 200,569 conventional Ford/Motorcraft service-part records in the publicly offered packaging CSV, downloaded October 4, 2026.
- Preserves the prior 690-base historical reference and 1,246 distinct bases from saved EPC observations; these add 529 bases beyond the current packaging snapshot. Two bases absent from the current download are retained separately as dated historical packaging evidence.
- 1,988 families have more specific names; 454 groups retain broad source descriptions. Generic terms such as bar, receiver and gasket kit are classified conservatively. A broad group can contain different components. Open the source evidence before selecting a service part.
- Count excluding two-digit body-style prefixes for comparison: **8,950**. This diagnostic is not used to alter or infer a service number.
- Leading zeros and seven/eight-character body bases are preserved. Service prefixes, suffixes, trim variants and duplicate source rows do not count as new bases.

This is a broad snapshot, **not every Ford base ever assigned**. It is not a VIN-fitment catalog or an interchange guarantee. Packaging descriptions can be terse; historical and EPC references supply additional names and context. System labels are navigation aids, partly inferred from standard numbering ranges. Exact original descriptions and example service numbers are available on each base.

### Example

Search **purge valve** → one **EVAP purge valve** card, with **9C915** and **9D289**. Details distinguish a valve from a vapor-line assembly with an integrated valve. The numbers are alternatives across applications, not interchangeable parts.

## Counter tools

- Saved parts, recent searches, grid/list views and light/dark themes.
- Per-part notes for bin locations and reminders.
- Job desk with separate vehicles, job/RO references, quantities, counter notes and selected full service numbers. VIN changes mark earlier selections for rechecking.
- Dedicated Snap-on menu: enter a VIN and search a common part name with the same offline family index. Choose the family and starting base; a different VIN opens its own job.
- Native guided Snap-on lookup on Android: VIN/base load after sign-in, with dropdowns for available locations, systems, sections and illustrations. Review application/date/quantity cards and return an employee-selected service number to the correct job.
- Optional **View diagram** on each guided service-part card. The app verifies the live part and current illustration before showing a memory-only native preview with pinch/drag, zoom buttons, Fit, Close and Back. Nothing opens automatically.
- Copy selected service numbers and combined quantities without the VIN or counter notes; stale, unrelated and unselected items are excluded. Copy, print or export a full job; mark it done or reopen it. Backups include all jobs, selected parts and the VIN decoder cache. Existing v1 worksheets, notes and saved parts migrate on upgrade.
- Native clipboard copy, CSV exports, CSV reference imports and JSON workspace backup/restore.
- Full-number breakdown; example service-number matching and conservative compact decoding.
- Offline catalog bundled in the APK. The local reference needs no login. Online VIN decoding uses NHTSA vPIC; EPC lookups require internet and your own Snap-on account. Source links open in the system browser. No app server, analytics or ads.

Browser and Android storage are separate. Back up the workspace before reinstalling or changing devices. The integrated VIN Decoder uses the official NHTSA vPIC service online for vehicle identity and manufacturer-reported specifications. It validates VIN format/check digit, accepts an optional model-year hint, displays source warnings, and caches the last 15 decoded vehicles for offline access. Missing fields remain unknown. It does not infer factory equipment, live prices, inventory, availability, supersessions or VIN fitment.

## VIN to service part

1. Open **Snap-on**. Enter the full VIN or its last eight characters and a common part description, synonym, base or service number. Confirm the resolved full vehicle identity before continuing with a short VIN.
2. Choose a matching family. Alternate bases stay together; choose the base to try first.
3. Tap **Find in integrated Snap-on**. Sign in on the phone through **Catalog / sign in** when needed; the desktop login does not transfer.
4. The app loads the VIN and searches the selected base. Use **Find parts** to retry or search another base. Keep VIN filters enabled.
5. Choose from the native location, system, section or illustration dropdowns. Tap **Open selection** to navigate. The catalog path lets you return to an earlier section.
6. Review service-part cards with the application, restrictions, build dates and quantity. Tap **Review this part**, then **Save to job**. The adapter checks the current VIN, base, catalog path and displayed part again before saving.

Existing family details and job rows also open this guided lookup. **Catalog / sign in** exposes the original catalog for authentication, diagrams or unsupported screens; **Guided lookup** returns to the app controls. **Load more catalog results** advances the rendered rows when the catalog uses a scrolling grid and retains up to 150 deduplicated observed part records for the current request, VIN, base and catalog path. Previously loaded parts return to their original viewport for a fresh live check before review. A newly seen continuation without its application heading is flagged for catalog review; missing restrictions are never inferred. The limit and loaded count are shown explicitly.

### Optional part diagram

Tap **View diagram** on a service-part card when you want a picture. The preview uses the original drawing currently rendered beside that same catalog's parts grid. It does not search the internet for a similar-looking part or infer an illustration from a base number. The verified row's **Call/Base** is shown as a label to find in the drawing; no service-specific highlight or hotspot is invented. Existing catalog selection overlays are excluded.

The image is generated only after a tap, stays in memory, and is discarded on close, pause or a changed catalog identity. Pixel fingerprints bind it to the current drawing as well as the VIN, base, request and part. Off-screen parts return to their original complete row for verification first. Use pinch/drag, **Zoom in**, **Zoom out** and **Fit**; **Close** or Android Back returns to the preserved results. Missing, incomplete, changed, unsupported or oversized illustrations offer the original catalog instead. Some parts do not have an illustrated view.

### Full VIN or last eight

The entry forms accept a full 17-character VIN or its last eight characters. Saved jobs and decoded vehicles can supply matching full VINs offline; every short-VIN match needs an explicit vehicle confirmation, even if there is only one. On Android, **Find full VIN in Snap-on** can resolve an unfamiliar suffix using the signed-in catalog. Confirm the returned full VIN and vehicle details before continuing. Ambiguous, missing or interrupted results require catalog attention or the full VIN; the previous vehicle displayed behind an error is never accepted as a new result.

Jobs and selected parts still use the complete VIN. Suffixes are never padded or treated as a vehicle identity, and they are not sent to NHTSA for a guessed decode. Existing full-VIN entry and workspace backups remain compatible. The browser version can recall saved full VINs; unfamiliar suffixes require the full VIN or Android's integrated catalog.

The interface reads the rendered page in an isolated Snap-on session. It is not a licensed Snap-on API integration or automatic fitment decision. Credentials are entered only on the original sign-in page and are not read by the adapter. Native choices are taken from recognized catalog navigation controls; ordering, picklist and CDK controls are not driven by the adapter. If the catalog changes or rejects its embedded session, the app shows an explicit catalog fallback. Lists contain observed rendered rows, not an exhaustive fitment result. The app keeps the catalog page warm between lookups for up to five minutes of idle time, with Activity-bound callbacks and contexts detached between screens. Memory pressure and background transitions can discard an idle session. This reduces repeated page loads but does not remove the website dependency or bypass sign-in.

Interrupted loads expose retry, cancellation and catalog fallback. Lookup results are accepted only for the active request, VIN and permitted base. Native dropdown selections and scroll position are retained only within the same catalog context.

The selectors were checked against an employee-authorized browser session. Automated Android tests use synthetic catalog fixtures; authenticated Android operation still needs verification with the employee's phone session. No private vehicle or newly accessed catalog records from that inspection are shipped in the source.

The browser version opens Snap-on separately and lets the employee record a reviewed number. The app does not send orders or write to CDK. Snap-on's documented CDK integration requires its supported local services/configuration on the counter workstation; see [Snap-on integration setup](https://docs.snaponbusinesssolutions.com/docs/EPC5Help/Ford/EPC5_Help_en-US/Content/ConfiguringIntegration.htm). Job copy/CSV is a handoff aid, not a CDK import contract.

## Android installation and builds

Android 8.0+ with a current Android System WebView. Download the signed release APK on the phone and follow the Android installation prompts. Keep the previous app until the backup has been restored and checked. Subsequent signed Ford Parts Desk versions update the same app when signed with the same key and a higher version code.

GitHub Actions builds and instruments the replacement identity `dev.urbanrunnerx.partsdesk`. It enables KVM on the hosted runner and runs the catalog/search tests, checks catalog reproducibility, builds Android APKs, runs Android lint and Android emulator tests for the offline app, guided controls and catalog adapter. Workflow artifacts include an **unsigned release** APK, a **debug test-only** APK, and test reports with fixture screenshots. They are not a signed user release. Only the signed APK attached to a release is the intended user download. Debug APK signatures can change between runs; do not install a debug APK over the signed release.

The repository provides the tag-triggered **Publish signed Android release** workflow. It runs the complete build/test workflow without signing credentials, signs only the release APK in a separate job, verifies its signature and existing certificate identity, and publishes `Ford-Parts-Desk.apk` plus `SHA256SUMS.txt` after checking the uploaded bytes. Only the publishing job has release-write permission. Missing or invalid signing inputs stop publication; there is no debug-key fallback.

**Maintainer setup is still required before using automated tag publication:** configure the four signing secrets in the `android-release` environment using the **same key that signed v3.0.0**. Back up that key and its passwords privately. See [RELEASING.md](RELEASING.md) for exact inputs, protection settings, version/tag rules and recovery steps. No signing credentials are stored in this repository, and this workflow change does not create another release.

Until that setup is complete, an explicitly approved release may be signed privately with the same established key after all checks pass, then published with its checksum. The private key is never a release asset. A separate private key backup remains pending.

Local build requirements: JDK 17, Gradle 8.13, Android SDK 35/build-tools 35.0.0.

```sh
npm test
python scripts/build-catalog.py
gradle :android:app:assembleRelease :android:app:lintDebug
```

## Separate replacement identity

Builds use application ID `dev.urbanrunnerx.partsdesk`, launcher name **Ford Parts Desk**, and an original parts-drawer icon. The previous app and this replacement have separate private storage and can coexist; neither removes or edits the other. No special build flag is required.

Workflow release output is unsigned; the published user APK is signed separately with the established release key. An ephemeral debug key is for emulator tests only, never a release/update identity.

### Bring over an existing workspace

1. Keep the previous app installed. In it, open **Tools → Back up my workspace** and save the JSON file somewhere you can select later, such as Downloads.
2. Install the signed Ford Parts Desk APK alongside the old app.
3. In Ford Parts Desk, open **Tools → Restore a workspace**, select the JSON backup, and approve replacing **Ford Parts Desk’s** workspace.
4. Check saved parts, jobs, quantities, selected service numbers and notes before relying on the new app. Recent searches, imported references, VIN decoder cache, theme and layout are included. Legacy schema-1 worksheets are supported too.
5. Sign in to Snap-on separately in Ford Parts Desk. Authentication cookies, passwords and in-progress catalog sessions do not transfer. Keep the old app until the migrated workspace has been checked.

Import reads only the backup you select; the app never reads another application's private storage. The restore validates the backup before writing and reverses completed writes if local storage is exhausted. No uninstall is required for this workflow.

For a browser update on the same origin, one structurally valid previous parts workspace can be copied into the new storage namespace. Its original values are retained. Ambiguous, interrupted or failed copies show a persistent recovery notice rather than silently appearing complete. Existing destination edits are not overwritten automatically; a successful manual JSON restore resolves the notice.

## Web preview

```sh
python -m http.server 8097 --directory dist
```

Open `http://localhost:8097`. All dependencies and reference files are local. A service worker caches the web version after the first successful load. Change the service-worker cache version when publishing content updates.

## Rebuild or extend the data

1. Download the [public Ford/Motorcraft packaging CSV](https://upccrossreference.cdis3.com/downloadfiles/packagingdata.csv).
2. Run `python scripts/import-packaging.py /path/to/packagingdata.csv --date YYYY-MM-DD`.
3. Review `data/packaging-refresh.json`: added, absent and changed bases are separate. Absent bases remain in `data/packaging-history.json` with their previous source date/hash; absence is not a discontinuation or supersession decision. The importer validates headers, exact reconstruction and unexpectedly smaller downloads before replacing data.
4. Review `data/families.json` for common names and documented multi-base groupings. Add new naming evidence and source links in `data/label-evidence.json`. Never add a base solely to reach a count.
5. Run `python scripts/build-catalog.py` and `npm test`; review changes to `data/catalog-audit.json` and the generated catalog.
6. The generated snapshot date follows the packaging retrieval date. Update the release version and APK `versionCode` only when publishing; a data refresh is not a release.

`data/packaging-reference.json` stores reduced factual evidence, up to three service examples per base, source-row counts and the SHA-256 of the raw CSV. The raw packaging file is not republished. Rebuilding from the reduced evidence is deterministic. The October 4 refresh added three exact bases, retained two absent bases as historical, and changed 438 existing reduced records. Five sourced name improvements cover nine existing bases. Bookmark IDs stay with the original broad group when a few bases are split into a more specific family. The test suite includes search/provenance checks and offline Python importer fixtures.

## Sources and branding

- [Ford / Motorcraft public packaging cross-reference](https://upccrossreference.cdis3.com/downloadfiles/packagingdata.csv)
- [Public Ford Basic Numbers quick reference](https://www.terminator-cobra.com/FordBasicNumber.pdf)
- Saved [Snap-on EPC](https://snaponepc.com/epc/#/) observations from the existing `urbanrunnerx/ford-base-finder` project (September 10, 2026); limited catalog coverage, no credentials or VIN records.
- [9D289 purge assembly example](https://www.fordpartsgiant.com/parts/ford-tube-asy-fuel-vapour-separat_k2gz-9d289-a.html)

Ford and Motorcraft names identify the vehicles and public reference sources. This independent workspace is not affiliated with Ford or Snap-on and is not an official electronic parts catalog. Interface and launcher artwork are original code-native assets; see `BRANDING.md`.
