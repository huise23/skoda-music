# PostHog Event Dictionary (S4)

Last Updated: 2026-04-27  
Module: `M-S4-OBS-006`  
Tasks: `T-S4-OBS-034`

## Common Properties
- `session_id`
- `device_id`
- `app_version`
- `build_number`
- `environment`
- `os_version`
- `network_type`
- `event_ts_client_ms`
- `source` (when applicable)

## Core Events
- `app_start`: app cold start entry
- `app_ready`: first frame ready and startup completed
- `app_foreground`: app enters foreground
- `app_background`: app enters background
- `play_start`: user/system starts playback of a track
- `play_success`: playback prepare success
- `playback_failed`: playback path failure with `stage` + `error_code`
- `pause`: playback paused
- `resume`: playback resumed
- `background_command_received`: notification/overlay/media_button/audio_focus command received
- `background_command_result`: command execution result (`handled` + `detail`)
- `resume_restore_attempt`: restore snapshot check started
- `resume_restore_success`: restore success and autoplay/seek resume ready
- `resume_restore_failed`: restore failed with stage-specific code

## Error Code Convention
- `CODEC_INIT_TIMEOUT`
- `DECODER_FAILURE`
- `NETWORK_FAILURE` / `NETWORK_<code>`
- `SOURCE_FAILURE` / `SOURCE_<code>`
- `SESSION_UNAVAILABLE`
- `RESUME_SNAPSHOT_INVALID`
- `RESUME_SESSION_MISSING`
- `UNKNOWN_FAILURE` / `UNKNOWN_<code>`

## Explicitly Forbidden Events
- progress tick events (e.g. every 200ms / 1s)
- each buffer state change
- each UI redraw/render callback
- HTTP headers or full response payload upload

## Notes
- Naming is snake_case only.
- Free-text message must stay in summary fields, not as primary aggregation key.
