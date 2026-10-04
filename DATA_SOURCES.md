# Public source provenance

The inherited EPC observations and legacy reference were verified on September 17, 2026 by unauthenticated HTTPS requests to the existing public repository. Both copies match the local source files exactly after newline normalization.

- https://raw.githubusercontent.com/urbanrunnerx/ford-base-finder/main/dist/epc-reference.json
  - SHA-256 (UTF-8, LF): ae88179241c530c8024520274566f5aaa8f93d27b2106763545c8ef12dba0e33
- https://raw.githubusercontent.com/urbanrunnerx/ford-base-finder/main/dist/reference.txt
  - SHA-256 (UTF-8, LF): 891b314d0903d972f196ead0d44b59fac6b55833a2462bd12a298ce4a7f691b1

New packaging records are extracted from Ford/Motorcraft's explicit public download at https://upccrossreference.cdis3.com/downloadfiles/packagingdata.csv. The raw file hash is saved in data/catalog-audit.json.

Ford Parts Desk uses original interface artwork documented in BRANDING.md. No account credentials, private files, customer VINs, or customer records are included. VIN notes and lookups created in the app remain local except the specific VIN submitted to NHTSA when the user chooses Decode VIN, or the VIN/base sent to Snap-on when the employee starts an EPC lookup. The employee signs into their own Snap-on session; credentials are not read by the app's page adapter, copied into the workspace, or sent to this repository.

Round 2 adds UI selectors and a visible-page adapter developed against the employee-authorized catalog workflow. No newly accessed private EPC catalog dump, customer record, or test vehicle VIN is included in the published app. Automated integration tests use explicitly synthetic part numbers and a public example VIN.

Round 3 adds app-owned dropdown navigation and part cards from recognized rendered catalog controls, plus a dedicated Snap-on entry menu backed by the existing offline search index. Native VIN/base/context verification is repeated at final save. The source catalog itself is unchanged. Browser inspection informed selectors only; new account catalog content and the employee test VIN are excluded. Test screenshots contain synthetic data.


## October 4, 2026 catalog refresh

The explicit public Ford/Motorcraft CSV download was retrieved again on October 4, 2026. SHA-256: `22f5db4f2d5152da1ecb799d496369f03fe21d00d562f603ca99d367b1999623`. The raw CSV remains outside the repository.

- 213,850 source rows; 200,569 accepted conventional service-part rows; 15,172 distinct current packaging bases.
- Three newly observed bases: `15026A46`, `18K810`, `2D142`. These are factual source additions, not generated range expansions.
- `3L548` and `76506A48` are absent from the new snapshot. Their September 17 records remain in `data/packaging-history.json` with the original hash/date. No discontinuation, invalidity or supersession is inferred.
- 438 existing reduced base records changed. `data/packaging-refresh.json` distinguishes description-name changes from description-count changes.
- The FCSD Packaging Engineering access guide identifies the published cross-reference workflow: https://upccrossreference.cdis3.com/UPC%20Cross%20Reference%20Data%20Access.pdf

`data/label-evidence.json` records checked source URLs and exact service examples for five naming improvements across nine existing bases. Three Ford-authored 2013 TSBs support transmission-range sensors, caliper anchor supports and trunk-lid torsion bars. Public retailer/dealer listings support door-opening and window-belt weatherstrip names. These source classes are identified separately. Only short factual descriptions and part identifiers are retained; no diagrams, prices, customer information or new paid EPC data are copied.

The TSB replacement-parts table is interpreted separately from the warranty dealer-coding base. A causal warranty base can differ from the replacement-part base. Historical TSBs and retail examples provide naming evidence, not current VIN fitment, availability, complete historical coverage or interchangeability. Handedness and upper/lower positions are not generalized from a single example.
