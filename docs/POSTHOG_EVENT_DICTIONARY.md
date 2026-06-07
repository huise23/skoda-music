# PostHog Event Dictionary (S4)

Last Updated: 2026-06-07
Module: `M-S4-OBS-006` + `M-S5-KG-035` + `M-S5-OBS-036` + `M-S5-KG-037`
Tasks: `T-S4-OBS-034` + `T-S5-KG-119` + `T-S5-OBS-120` + `T-S5-KG-123/124/125/126/127`

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
- `kugou_qr_refresh_start`: QR refresh request started
- `kugou_qr_refresh_success`: QR key and image loaded
- `kugou_qr_refresh_failed`: QR key/image/network failure, fail-soft and retryable
- `kugou_qr_poll_failed`: QR polling failed, expired, or returned no usable status
- `kugou_qr_login_success`: QR polling returned login success and local validation starts
- `kugou_session_validation_success`: direct session device/token validation succeeded
- `kugou_session_validation_deferred`: QR token is usable but device/token refresh was deferred fail-soft
- `kugou_session_validation_failed`: direct session device/token validation failed and login remains blocked
- `resume_restore_skipped`: startup source gate skipped Emby resume restore under default Kugou mode
- `emby_auto_refresh_skipped`: startup source gate skipped Emby recommendation auto-refresh under default Kugou mode
- `kugou_direct_content_request`: Android direct Kugou content request started for a minimal direct path
- `kugou_direct_play_url_request`: Android direct Kugou play URL request started
- `kugou_direct_play_url_success`: Android direct Kugou play URL resolved
- `kugou_direct_play_url_failed`: Android direct Kugou play URL failed without exposing URL/query; includes `failure_kind`, `error_code`, `priv_status`, `err_code`
- `kugou_daily_vip_start`: Daily one-day VIP flow started
- `kugou_daily_vip_success`: Daily one-day VIP flow completed, skipped because already received, or local fallback skip applied
- `kugou_daily_vip_failed`: Daily one-day VIP record/receive/upgrade failed with retry cooldown
- `kugou_content_load_success`: Kugou content list loaded, with `stage` and `item_count`
- `kugou_content_load_failed`: Kugou content list failed and enters retry/login recovery
- `kugou_auth_dialog_shown`: login dialog opened for a missing/expired Kugou session; properties use redacted `stage` and `pending_action`
- `kugou_auth_recovery_resume`: login succeeded and a pending recovery action is consumed; properties use redacted `stage` and `pending_action`
- `kugou_post_login_auto_load`: default Kugou home recommendation load is triggered after login or cached session restore
- `kugou_like_request`: direct Kugou like add request started
- `kugou_like_success`: direct Kugou like add succeeded
- `kugou_like_failed`: direct Kugou like add failed without exposing song hash, token, or payload
- `kugou_queue_start`: normal Kugou queue starts from a visible content context
- `kugou_radio_session_start`: Kugou radio/FM session starts from radio songs

## Error Code Convention
- `CODEC_INIT_TIMEOUT`
- `DECODER_FAILURE`
- `NETWORK_FAILURE` / `NETWORK_<code>`
- `SOURCE_FAILURE` / `SOURCE_<code>`
- `SESSION_UNAVAILABLE`
- `RESUME_SNAPSHOT_INVALID`
- `RESUME_SESSION_MISSING`
- `WIFI_NOT_CONNECTED`
- `QR_KEY_UNAVAILABLE`
- `QR_IMAGE_UNAVAILABLE`
- `QR_POLL_EXCEPTION`
- `QR_POLL_EMPTY_OR_FAILED`
- `QR_EXPIRED`
- `SESSION_VALIDATION_FAILED`
- `KUGOU_CONTENT_FAILED`
- `KUGOU_DIRECT_CONTENT_FAILED`
- `KUGOU_DIRECT_LIKE_FAILED`
- `UNKNOWN_FAILURE` / `UNKNOWN_<code>`

## Explicitly Forbidden Events
- progress tick events (e.g. every 200ms / 1s)
- each buffer state change
- each UI redraw/render callback
- HTTP headers or full response payload upload
- tokens, session keys, cookies, phone numbers, verification codes, full URL queries, auth headers, private API keys, or reusable device credentials

## Notes
- Naming is snake_case only.
- Free-text message must stay in summary fields, not as primary aggregation key.
