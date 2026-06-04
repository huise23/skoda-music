# Architecture

## Purpose

This file records architecture boundaries for AI-assisted development.

## Detected Project Architecture

- Project type: Android head-unit music player, pending user confirmation.
- Main app: `app/` Kotlin Android application with XML views and native C++ DSP bridge.
- Native layer: `app/src/main/cpp/` C++ code built through CMake.
- Reference / planning code: root `src/` C++ / QML skeletons and examples.
- Third-party reference: `KugouMusic.NET/` is external reference material and must not be modified by Android implementation tasks.

## Current Layers

- Android entry points:
  - `app/src/main/java/com/skodamusic/app/MainActivity.kt`
  - `app/src/main/java/com/skodamusic/app/playback/PlaybackService.kt`
  - `app/src/main/java/com/skodamusic/app/playback/MediaButtonReceiver.kt`
- UI / presentation:
  - `app/src/main/res/layout/`
  - `app/src/main/java/com/skodamusic/app/ui/`
- Source integrations:
  - `app/src/main/java/com/skodamusic/app/emby/`
  - `app/src/main/java/com/skodamusic/app/kugou/`
- Playback:
  - `app/src/main/java/com/skodamusic/app/player/`
  - `app/src/main/java/com/skodamusic/app/playback/`
- Audio / DSP:
  - `app/src/main/java/com/skodamusic/app/audio/`
  - `app/src/main/java/com/skodamusic/app/audio/dsp/`
  - `app/src/main/cpp/`
- Persistence / state:
  - `app/src/main/java/com/skodamusic/app/data/`
  - `app/src/main/java/com/skodamusic/app/like/`
  - `app/src/main/java/com/skodamusic/app/playback/*Store.kt`

## Architecture Rules

1. Keep Android entry points thin. `MainActivity`, services, receivers, and future fragments/activities may handle lifecycle, wiring, and delegation, but must not absorb unrelated feature logic.
2. Keep API17 compatibility visible in every architecture change. Do not introduce an AndroidX, media, navigation, audio, or background-execution pattern that requires `minSdk > 17`.
3. Keep source-specific behavior behind source-owned modules. Emby queue semantics must not be reused to represent Kugou song queue or Kugou radio/FM session behavior.
4. Keep native DSP code isolated behind bridge/controller APIs. UI code should consume stable state and commands, not native implementation details.
5. Treat `KugouMusic.NET/` as read-only reference. Android code may consult it for protocol, queue, radio, and auth behavior, but must not edit it as part of this repo's implementation.
6. Prefer binder/controller extraction before large page shell rewrites. Fragment or Activity splits are allowed only after API17, background control, floating window, and left-navigation behavior are accounted for.
7. Avoid circular ownership between UI, playback service, source clients, and stores.

## Entry Point Rules

Entry points may:

- bind views and lifecycle callbacks
- route user actions to focused controllers
- observe state and render high-level transitions
- delegate source, playback, cache, auth, and DSP work

Entry points must not:

- implement large source clients or protocol logic
- own queue/radio state machines directly
- perform direct persistence beyond simple store wiring
- contain large reusable row/render helpers that can live in `ui/`
- accumulate unrelated settings, playback, source, and diagnostics behavior in one file

## Project-Specific Risk

- `MainActivity.kt` is already above the red-line size threshold and must be actively decomposed before adding new responsibilities.
- API17 / Android 4.2.2 is a mandatory platform baseline.
- `KugouMusic.NET/` is a third-party reference project and must be ignored by local code health scanning.
