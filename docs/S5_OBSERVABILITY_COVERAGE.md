# S5 Observability Coverage

Last Updated: 2026-06-05

## Purpose

Record the minimum diagnostic evidence expected for S5 features without turning PostHog into a raw log sink.

## Coverage Matrix

| Area | Evidence | Sensitive Filter |
| --- | --- | --- |
| QR refresh | PostHog: `kugou_qr_refresh_start`, `kugou_qr_refresh_success`, `kugou_qr_refresh_failed` | No token, session key, URL query, cookie, phone, verification code |
| QR polling | PostHog: `kugou_qr_poll_failed`, `kugou_qr_login_success` | No QR key, token, nickname, user id |
| Session validation | PostHog: `kugou_session_validation_success`, `kugou_session_validation_deferred`, `kugou_session_validation_failed` | No token, dfid, mid, uuid, device credential |
| Default Kugou source gate | PostHog: `resume_restore_skipped`, `emby_auto_refresh_skipped`; runtime log includes skip reason | No Emby URL, username, password, token, cached queue payload |
| Kugou content | PostHog: `kugou_content_load_success`, `kugou_content_load_failed` with `stage` and `item_count` | No request URL, response body, session |
| Kugou normal queue | PostHog: `kugou_queue_start`; runtime log keeps visible click context | No track title payload upload, no raw hash in PostHog |
| Kugou radio | PostHog: `kugou_radio_session_start`; runtime log keeps visible click context | No raw radio payload upload |
| DSP direct bridge | Runtime/logcat: `hifi-dsp native direct-buffer bridge ...`, `hifi-dsp native status=...`, `hifi-dsp bypass ...` | Not sent to PostHog from audio hot path |

## Explicit Non-Goals

- No high-frequency progress, audio-frame, DSP per-buffer, or UI redraw events in PostHog.
- No full HTTP payload, headers, full URL query, token, session key, cookie, phone number, verification code, private API key, or reusable device credential in PostHog/runtime logs.

## Validation

- Logcat should show `SkodaPostHog capture ok event=<event>` or `capture failed/exception` samples.
- API17 regression evidence should include at least one QR auth event and one Kugou content/queue/radio event when the corresponding flow is tested.
