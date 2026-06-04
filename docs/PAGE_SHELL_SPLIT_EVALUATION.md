# Page Shell Split Evaluation

Last Updated: 2026-06-04

## Scope

This evaluates `T-S5-MAIN-116`: whether the current API17 Android shell should pilot a Fragment, an independent Activity, or continue focused Binder extraction before a page-shell split.

## Constraints Checked

- `minSdk = 17` remains mandatory.
- Left-side first-level navigation must remain stable.
- Background playback, media buttons, foreground notification, floating window, and service state must not regress.
- `MainActivity.kt` is still above the entry-file red line and must not receive more page business logic.
- `KugouMusic.NET/` is not part of this evaluation and remains read-only.

## Candidate Assessment

### Independent Activity

Decision: defer.

Reason:
- Moving Settings, EQ, logs, or Kugou pages into a second Activity would split the current left-navigation shell.
- Back behavior, foreground service state, floating window entry points, and now-playing coordination would need cross-Activity state sync.
- This is technically possible on API17, but it is too broad for the current corrective stage.

### Fragment

Decision: viable future pilot, but not as an immediate direct migration.

Reason:
- Existing AppCompat/AndroidX dependencies can support Fragment on API17.
- A Fragment pilot would still require extracting the target page layout from `activity_main.xml`, moving view binding, and making lifecycle ownership explicit.
- Doing this before low-coupling page binders are extracted would concentrate lifecycle, navigation, and state-sync risk in one change.

Preferred future pilot:
- EQ page or runtime log page.
- These pages are lower risk than the playback page, service bridge, queue, or Kugou content pages.

### Continue Binder Extraction First

Decision: selected route for the next implementation slice.

Reason:
- This keeps the current left navigation and single host stable while reducing `MainActivity` ownership.
- It prepares a clean Fragment boundary later: each future Fragment can wrap an existing Binder instead of inheriting raw Activity logic.
- It avoids introducing a second lifecycle boundary while S5 still needs final validation.

Recommended next page-shell prep tasks:

1. Extract `RuntimeLogBinder`:
   - Own runtime log buffer, preview rendering, fullscreen dialog, copy/clear actions.
   - Low playback risk.
   - Good first Fragment candidate after extraction.

2. Extract `EqualizerPageBinder`:
   - Own EQ fullscreen page controls, mode/preset/band rendering, back action callback.
   - Medium risk due DSP settings, but still lower than playback/service pages.

3. Re-evaluate Fragment pilot:
   - Move one extracted binder page into a Fragment hosted inside the existing right-side content frame.
   - Keep left navigation in `MainActivity`.
   - Do not introduce a navigation component dependency.

## Decision

For `T-S5-MAIN-116`, the safe page-shell decision is:

- Do not introduce an independent Activity in this stage.
- Do not directly migrate a raw XML page to Fragment before its Binder exists.
- Continue Binder extraction first, with `RuntimeLogBinder` as the preferred pilot prep.
- Revisit Fragment after the target page has a focused Binder and stable lifecycle contract.

## Validation

This evaluation does not change runtime code. Required validation:

- `git diff --check`
- `./scripts/check_api17_guardrails.sh`
- `python scripts/check_code_health.py` to confirm no new code-health regression
