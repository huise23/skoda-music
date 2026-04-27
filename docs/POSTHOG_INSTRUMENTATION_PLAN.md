# PostHog Integration Plan (S4)

Last Updated: 2026-04-27  
Scope: `M-S4-OBS-006` (`T-S4-OBS-034/035/036/037/038/039`)

## 1) Goal and Positioning
- PostHog is used as a **structured event stream** for diagnostics, regression evidence, and AI analysis.
- PostHog is **not** used as a full raw log store.
- Integration must be **API17 compatible**, **fail-open**, and **low overhead**.

## 2) In/Out of Scope

### In Scope
- Core lifecycle and playback path events.
- Background control events (notification/overlay/media button/audio focus).
- Failure-stage and error-code events for fast aggregation.
- Query/export flow for single-session replay and AI input.

### Out of Scope
- Full-text verbose debug logs.
- High-frequency state stream as events.
- Full HTTP headers/body upload.
- Self-hosted PostHog deployment in this phase.

## 3) Hard Constraints
- `minSdk=17` must remain unchanged.
- Emby-only / IPv4-only constraints remain unchanged.
- Event reporting failures must never block playback or UI control.
- Sensitive data must not be uploaded (token/password/full response body).

## 4) Event Budget and Noise Guard

### Per-session Target Budget
- Normal session target: `<= 80` events/session.
- Error-heavy session soft cap: `<= 150` events/session.
- Over-cap behavior: drop low-priority diagnostic events first.

### Explicitly Forbidden (Do Not Upload)
- Progress tick every 200ms/1s.
- Every buffering state transition.
- Every UI redraw/render callback.
- Every HTTP header or full payload.

### Throttling Rules
- Same `event + key properties` repeated within `10s`: coalesce once.
- Same error (`error_code + stage + track_id`) repeated within `30s`: coalesce once.
- Heartbeat-like events: disable by default; only enable temporary debug switch.

## 5) Event Model (Core Set)

## 5.1 Common Properties (All Events)
- `session_id` (business session, app cold start scoped)
- `device_id` (stable install/device id)
- `app_version`
- `build_number`
- `os_version`
- `network_type`
- `event_ts_client_ms`
- `source` (if applicable: `ui/notification/overlay/media_button/audio_focus/service`)

## 5.2 Lifecycle
- `app_start`
  - props: `cold_start`, `launch_source`
- `app_ready`
  - props: `startup_ms`
- `app_background`
- `app_foreground`
- `app_exit` (best effort)

## 5.3 Playlist / Load
- `playlist_load_start`
  - props: `playlist_type`
- `playlist_load_success`
  - props: `duration_ms`, `track_count`
- `playlist_load_failed`
  - props: `duration_ms`, `error_code`, `http_status`, `stage=playlist_load`

## 5.4 Playback
- `play_start`
  - props: `track_id`, `position_ms`, `from_source`
- `play_success`
  - props: `track_id`, `prepare_ms`, `decoder`
- `playback_failed`
  - props: `track_id`, `stage`, `error_code`, `error_summary`
- `skip_next`
  - props: `track_id_before`, `track_id_after`, `source`
- `pause`
  - props: `track_id`, `source`
- `resume`
  - props: `track_id`, `source`

## 5.5 Background Control
- `background_command_received`
  - props: `action`, `source`
- `background_command_result`
  - props: `action`, `source`, `handled`, `detail`

## 5.6 Resume / Recovery
- `resume_restore_attempt`
  - props: `has_snapshot`, `snapshot_age_ms`
- `resume_restore_success`
  - props: `track_id`, `position_ms`
- `resume_restore_failed`
  - props: `stage`, `error_code`, `error_summary`

## 5.7 Error System
- `network_error`
  - props: `stage`, `error_code`, `http_status`
- `decoder_error`
  - props: `track_id`, `error_code`, `decoder`, `stage`
- `uncaught_exception`
  - props: `exception_type`, `stack_summary`, `fatal`

## 6) Error Code Convention
- Use stable uppercase codes:
  - `NETWORK_TIMEOUT`
  - `HTTP_401`
  - `HTTP_5XX`
  - `CODEC_INIT_TIMEOUT`
  - `AUDIO_FOCUS_LOSS`
  - `CONTROLLER_UNAVAILABLE`
  - `RESUME_SNAPSHOT_INVALID`
- Avoid free text as primary key; keep text in `error_summary` only.

## 7) Architecture Plan

## 7.1 Reporter Layer
- Add a lightweight reporter component (suggested: `PostHogReporter`).
- Reuse existing `OkHttp 3.12.13`.
- Async send with bounded queue and timeout.
- Fail-open by default:
  - send failure => local log only
  - no exception propagation to business flow

## 7.2 Config and Switch
- Runtime switch:
  - `enabled` boolean
  - `host`
  - `project_api_key`
  - `environment` (`dev/prod`)
- Missing/invalid config => reporter no-op mode.

## 7.3 Privacy Guard
- Denylist:
  - password/token/full auth payload
  - full HTTP response body
  - full stack trace (keep summary only)
- Max field length per string (e.g. 256 chars), truncate beyond limit.

## 8) Integration Points (Current Codebase)
- `MainActivity.kt`
  - lifecycle events: start/ready/background/foreground
  - playback events: play_start/success/failed/pause/resume/skip
- `PlaybackService.kt`
  - background command received/result
  - audio focus loss-related events
- `PlaybackStateStore.kt`
  - command trace bridge fields for background result reporting

## 9) Rollout Plan

## Phase P0: Schema and Access
- Finalize event dictionary and property schema.
- Confirm PostHog host/key/environment policy (`T-S4-OBS-039`).

## Phase P1: Reporter and Guardrail
- Implement API17-safe reporter (`T-S4-OBS-035`).
- Add throttle/coalesce + privacy guard (`T-S4-OBS-037`).

## Phase P2: Instrumentation
- Wire key events in activity/service paths (`T-S4-OBS-036`).
- Keep feature flags for quick disable.

## Phase P3: Validation and Query
- Validate with scenario scripts and event sequence checks (`T-S4-OBS-038`).
- Export one full session for AI analysis.

## 10) Functional Validation Matrix

| Scenario | Expected Event Chain |
|---|---|
| Cold start success | `app_start -> app_ready` |
| Playlist success then play success | `playlist_load_start -> playlist_load_success -> play_start -> play_success` |
| Playback decoder failure | `play_start -> playback_failed(stage=decoder_init, error_code=...)` |
| Notification next | `background_command_received(source=notification) -> background_command_result(handled=...)` |
| Overlay play/pause | `background_command_received(source=overlay) -> background_command_result(...)` |
| Media button next/prev | `background_command_received(source=media_button) -> background_command_result(...)` |
| Resume success | `resume_restore_attempt -> resume_restore_success` |
| Resume failure | `resume_restore_attempt -> resume_restore_failed` |

## 11) Query and AI Export
- Required queries:
  - failure distribution by `error_code` in 7 days
  - failure distribution by `stage`
  - single `session_id` timeline
  - version comparison (`app_version/build_number`)
- AI export payload should include:
  - ordered event list by `session_id`
  - `event`, `ts`, `stage`, `error_code`, `source`, `track_id`, `duration_ms`

## 12) Done Criteria for OBS Module
- Core event set queryable in PostHog.
- No high-frequency noise events present.
- Reporter fail-open verified by fault injection (network down / bad host).
- One session export successfully used for diagnostic replay.
