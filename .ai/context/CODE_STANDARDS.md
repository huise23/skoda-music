# Code Standards

## Purpose

These standards keep changes readable, reviewable, modular, and compatible with the current Android API17 baseline.

## General Standards

1. Prefer clear structure over compact cleverness.
2. Keep files, classes, and functions focused on one primary responsibility.
3. Follow existing Kotlin, Android XML, CMake, and C++ style in nearby files.
4. Do not introduce new third-party dependencies without a reason tied to current scope.
5. Preserve useful error context; do not silently swallow integration, playback, cache, or native failures.
6. Add comments for non-obvious compatibility, lifecycle, threading, native, networking, or recovery behavior.
7. Keep API17 compatibility checks explicit when using Android framework or dependency APIs.
8. Do not modify `KugouMusic.NET/`; use it as read-only reference only.

## Size Thresholds

Line count is a signal, not the only quality metric. Mixed responsibilities should be split even below these thresholds.

| Unit | Preferred | Warning | Refactor | Red |
| --- | ---: | ---: | ---: | ---: |
| General source file | 500 | 800 | 1200 | 2000 |
| Entry point file | 500 | 800 | 1200 | 1800 |
| Declarative UI file | 700 | 1200 | 1800 | 2500 |
| Test file | 800 | 1500 | 2500 | 4000 |
| Class / component / service | 300 | 600 | 1000 | 1500 |
| Function / method | 60 | 120 | 200 | 300 |

## Android / Kotlin Standards

- Keep `MainActivity`, services, receivers, and future fragments/activities as lifecycle and wiring shells.
- Move reusable rendering to `ui/`, playback orchestration to `playback/` or `player/`, source API behavior to source-owned modules, and persistence to stores.
- Prefer API17-safe platform APIs and dependency versions already proven in this repo.
- Avoid UI refresh loops that are too aggressive for the target AC83xx head unit.
- Keep background playback, media button handling, foreground notification, and floating window behavior stable when changing page structure.

## C++ / Native Standards

- Keep native DSP code behind stable bridge/controller boundaries.
- Avoid exposing native implementation details to UI code.
- Prefer explicit ownership and simple data flow across JNI/native boundaries.
- Treat performance-sensitive or fail-open logic as comment-worthy.

## Testing And Validation

Run applicable checks after implementation:

- `git diff --check`
- `./scripts/check_api17_guardrails.sh`
- `python scripts/check_code_health.py`
- `gradle :app:compileDebugKotlin --no-daemon`
- `gradle :app:assembleDebug --no-daemon`

If validation cannot be run, record why and provide the exact command.
