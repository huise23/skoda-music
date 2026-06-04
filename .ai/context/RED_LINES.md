# Red Lines

These constraints are mandatory. If a task conflicts with any red line, stop and ask for confirmation before continuing.

## Platform Red Lines

- `minSdk = 17` is a hard red line.
- Android 4.2.2 / API17 behavior must remain supported.
- Do not introduce dependencies, Android framework APIs, navigation patterns, media APIs, audio APIs, or background execution approaches that require `minSdk > 17`.
- Do not "fix" compatibility problems by raising `minSdk`, `target` assumptions, or dependency baselines.
- Java/Kotlin bytecode must remain compatible with the project's current Android toolchain constraints.

## Third-Party Reference Red Lines

- `KugouMusic.NET/` is a third-party/reference project in this workspace.
- Do not modify files under `KugouMusic.NET/` unless the user explicitly asks for changes to that project.
- Code health, bootstrap, and Android implementation tasks must ignore `KugouMusic.NET/` as project source.
- Kugou behavior in the Android app must be traceable to `KugouMusic.NET/` reference code or stop for clarification.

## Architecture Red Lines

- Do not add new responsibilities to `MainActivity.kt`; extract focused binders/controllers/renderers first.
- Do not create or expand god files, god classes, god components, or god functions.
- Do not mix UI rendering, source API protocols, playback queue state, persistence, and native DSP internals in one unit.
- Do not perform large architecture changes during execution without updating scope/plan first.
- Do not add a new Activity, Fragment, or page shell if it regresses API17 compatibility, left-side navigation, background playback, steering-wheel/media buttons, foreground notification, or floating window behavior.

## Size Red Lines

- Manually maintained general source file red line: 2000 lines.
- Entry point red line: 1800 lines.
- Declarative UI file red line: 2500 lines.
- Class/component/service red line: 1500 lines.
- Function/method red line: 300 lines.

Existing files above red line must be treated as refactoring targets, not convenient places for more behavior.

## Dependency Red Lines

- Do not introduce a third-party dependency without documenting why existing project tools are insufficient.
- Do not introduce a dependency whose manifest, bytecode, transitive APIs, or runtime behavior conflicts with API17.
- Do not replace the current legacy-compatible playback route with a higher-API media stack unless the user explicitly approves a new scope.

## Security / Performance Red Lines

- Do not log secrets, session keys, tokens, phone numbers, or private API credentials.
- Do not block the UI thread with network, disk, native DSP, or heavy parsing work.
- Do not add high-frequency UI polling on the target low-end head unit.
- Do not hide playback, cache, network, auth, or native fail-open errors just to keep UI green.
