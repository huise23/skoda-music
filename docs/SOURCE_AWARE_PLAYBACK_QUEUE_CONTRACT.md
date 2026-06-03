# Source-Aware Playback And Queue Contract

Last Updated: 2026-06-03

## Scope
- Defines the S5 boundary for Emby and Kugou coexistence in queue, playback URL resolution and cache guard.
- This is a design/contract task; UI skeleton and real Kugou playback implementation are separate tasks.

## Current Baseline
- `MainActivity` stores queue state in:
  - `loadedTracks: List<EmbyTrack>`
  - `libraryTracks: List<EmbyTrack>`
  - `currentTrackIndex: Int`
- `playTrackAtCurrentIndex(...)` builds Emby playback URLs with `embyApi.buildDownloadUrl(...)`.
- Download/cache state is keyed by Emby track id through `TrackDownloadState`.
- Current Emby playback is download-only and must remain stable while source abstraction is introduced.

## Target Queue Model
- Queue item should carry:
  - `SourceTrack`
  - `SourcePlaybackRef`
  - source-specific display metadata
  - stable queue identity
- First implementation may keep legacy `loadedTracks` for Emby while introducing a parallel source-aware adapter. Do not hard-switch all playback paths in one patch.

## Source Playback Resolver

### Common Contract
- Input: `SourcePlaybackRef`
- Output:
  - playable URL
  - headers if required
  - duration hint if available
  - cache policy
  - failure reason
- Failure must be explicit and recoverable:
  - not logged in
  - network failure
  - unavailable/VIP/purchase required
  - invalid response
  - unsupported source

### Emby Resolver
- `MusicSource.EMBY`
- Keeps current behavior:
  - `primaryId = Emby item id`
  - uses `embyApi.buildDownloadUrl(base, itemId, token)`
  - keeps existing download-only strategy and 30s download window behavior.
- Does not change current Emby host/DNS/IPv4 policy.

### Kugou Resolver
- `MusicSource.KUGOU`
- Uses `docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md`:
  - `GET /song/url`
  - `SongClient.GetPlayInfoAsync(...)`
  - `RawSongApi.GetUrlAsync(...)`
  - model `PlayUrlData`
- Required playback ref fields:
  - `hash`
- Optional fields:
  - `albumId`
  - `albumAudioId`
  - `quality`, default `"128"`
  - `freePart`
- If not authenticated, return `not_logged_in` and route user to Kugou login.
- If `PlayUrlData` indicates VIP/purchase/empty URL, return a user-facing unavailable state instead of falling back to Emby.

## Cache Guard
- S5 cache target remains max `100MB` total for local playback/cache data tied to online sources.
- Emby existing cache cleanup stays in place.
- Kugou playback cache, when implemented, must:
  - count toward the same 100MB budget or a stricter sub-budget
  - evict least-recent temporary items first
  - never block UI during cleanup
  - never be uploaded to Emby in S5 Ready tasks
- `B-KG-EMBY-INGEST-001` remains blocked until Emby upload/ingest support is confirmed.

## Like / Ingest Status Interaction
- Like status should reference `SourceTrack` and `MusicSource`.
- Kugou like may be local-only or account-synced depending on the implementation task, but UI must show which state it represents.
- Emby ingest state values:
  - `blocked_ingest`
  - `pending_confirmation`
  - `failed`
  - future `uploaded` only after Emby capability is confirmed and implemented.

## Migration Steps
1. Add source model types. Done in `MainModels.kt`.
2. Add mappers:
   - `EmbyTrack -> SourceTrack`
   - Kugou recommended song -> `SourceTrack`
   - Kugou playlist song -> `SourceTrack`
   - Kugou radio song -> `SourceTrack`
3. Introduce source-aware queue state without removing legacy Emby queue.
4. Route Emby playback through an Emby resolver wrapper that preserves existing URL generation.
5. Add Kugou resolver using WebApi `/song/url`.
6. Gate Kugou resolver on session state.
7. Add cache budget enforcement before enabling large Kugou playback cache.

## Non-Goals
- No Emby upload ingest.
- No service-side media library write.
- No direct raw Kugou protocol port.
- No queue persistence format replacement until source-aware restore is explicitly planned.
