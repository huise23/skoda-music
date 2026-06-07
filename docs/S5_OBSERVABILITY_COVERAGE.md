# S5 Observability Coverage

Last Updated: 2026-06-07

## Purpose

Record the minimum diagnostic evidence expected for S5 features without turning PostHog into a raw log sink.

## Coverage Matrix

| Area | Evidence | Sensitive Filter |
| --- | --- | --- |
| QR refresh | PostHog: `kugou_qr_refresh_start`, `kugou_qr_refresh_success`, `kugou_qr_refresh_failed` | No token, session key, URL query, cookie, phone, verification code |
| QR polling | PostHog: `kugou_qr_poll_failed`, `kugou_qr_login_success` | No QR key, token, nickname, user id |
| Session validation | PostHog: `kugou_session_validation_success`, `kugou_session_validation_deferred`, `kugou_session_validation_failed` | No token, dfid, mid, uuid, device credential |
| Login dialog/recovery | PostHog: `kugou_auth_dialog_shown`, `kugou_auth_recovery_resume`, `kugou_post_login_auto_load`; runtime log includes redacted reason/action | No token, session key, QR key, QR image URL, nickname, user id, phone, verification code |
| Default Kugou source gate | PostHog: `resume_restore_skipped`, `emby_auto_refresh_skipped`; runtime log includes skip reason | No Emby URL, username, password, token, cached queue payload |
| Kugou content | PostHog: `kugou_direct_content_request`, `kugou_content_load_success`, `kugou_content_load_failed` with `stage` and `item_count` | No request URL, response body, session |
| Kugou daily recommend | PostHog: `kugou_post_login_auto_load`, `kugou_direct_content_request`, `kugou_content_load_success`, `kugou_queue_start`; runtime log records daily coordinator state transitions | No raw song hash/title payload upload, token, session, request URL, response body |
| Kugou Scene | PostHog: `kugou_direct_content_request`, `kugou_content_load_success`, `kugou_content_load_failed`, `kugou_scene_tab_toggle`, `kugou_queue_start`; runtime log records only short scene/hash IDs | No token, userid, dfid, mid, full query, scene response body, raw hash in PostHog |
| Kugou play URL | PostHog: `kugou_direct_play_url_request`, `kugou_direct_play_url_success`, `kugou_direct_play_url_failed` | No play URL, hash, token, dfid, mid, full query |
| Kugou daily VIP | PostHog: `kugou_daily_vip_start`, `kugou_daily_vip_success`, `kugou_daily_vip_failed` | No token, userid, full response body, full query |

## PostHog Query Limitation
- 2026-06-07 checked local repo/env: only capture/project API key is available; no PostHog personal/query API token was found.
- When query access is available, inspect recent `kugou_direct_play_url_failed` events grouped by `error_code`, `failure_kind`, `priv_status`, `err_code`, and build/session metadata.
| Kugou like | PostHog: `kugou_like_request`, `kugou_like_success`, `kugou_like_failed`; runtime log includes only HTTP/status on failure | No song hash/title payload upload, token, userid, list payload, request URL, or response body |
| Kugou normal queue | PostHog: `kugou_queue_start`; runtime log keeps visible click context | No track title payload upload, no raw hash in PostHog |
| Kugou radio | PostHog: `kugou_radio_session_start`; runtime log keeps visible click context | No raw radio payload upload |
| Queue auto-scroll / grid UI | Runtime/UI evidence only: screenshot/video proving current row centered and Radio/Scene cards render thumbnails | No PostHog UI redraw or scroll tick events |
| DSP direct bridge | Runtime/logcat: `hifi-dsp native direct-buffer bridge ...`, `hifi-dsp native status=...`, `hifi-dsp bypass ...` | Not sent to PostHog from audio hot path |

## Explicit Non-Goals

- No high-frequency progress, audio-frame, DSP per-buffer, or UI redraw events in PostHog.
- No full HTTP payload, headers, full URL query, token, session key, cookie, phone number, verification code, private API key, or reusable device credential in PostHog/runtime logs.

## Validation

- Logcat should show `SkodaPostHog capture ok event=<event>` or `capture failed/exception` samples.
- API17 regression evidence should include at least one QR auth event, one auth dialog/recovery event, one Kugou content/queue/radio event, and one Scene/grid/current-queue screenshot when the corresponding flow is tested.
