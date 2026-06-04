# Kugou Auth And Session Contract

Last Updated: 2026-06-04

## Scope
- Covers S5 Android login/session behavior for Kugou mode.
- Source of truth: `docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md` and `KugouMusic.NET/`.
- Default login method: QR code.
- Secondary login method: mobile SMS code.

## Session Transport
- Android must not ask the user for a Kugou WebApi Base URL.
- The previous WebApi session mode used `X-Kg-Session-Id` / `kg_sid` and stored token state server-side, but that product path is now disabled.
- Android now has a direct QR key/check path, then runs `.NET`-equivalent device init and token refresh before treating the session as usable.
- SMS AES/RSA login is still pending follow-up direct port.

## Login State
- A session is considered authenticated only when:
  - server-side `KgSession.Token` is not blank
  - server-side `KgSession.UserId != "0"`
- Source:
  - `UserClient.IsLoggedIn()`
  - `LoginClient.RefreshSessionAsync()`
  - `KgSessionManager.UpdateAuth(...)`
  - `KgSessionManager.Logout()`
- Android should treat these as not authenticated:
  - no cached direct QR session token
  - cached direct token exists but `validation_state != valid`
  - refresh token result has `Status != 1`
  - any content endpoint returns a failed `KgBaseModel` status that corresponds to auth/session failure
  - session reset or logout

## QR Login Flow
1. Android opens Kugou login page/dialog in QR mode.
2. Call `RawLoginApi.GetQrKeyAsync()` equivalent through Android-owned direct client.
3. Show `QRCode.QrcodeImg`; keep `QRCode.Qrcode` as poll key.
4. Poll `RawLoginApi.CheckQrStatusAsync(key)` equivalent every 2 seconds.
5. State mapping from `.NET LoginViewModel`:
   - `WaitingForScan`: show waiting scan state.
   - `WaitingForConfirm`: show scanned, waiting phone confirmation.
   - `Success`: stop polling, cache returned `userid/token/nickname` as pending, then run device init and token refresh.
   - `Expired`: stop polling and show refresh QR action.
6. On page leave or login mode switch, stop polling.
7. Only after device init and token refresh pass does Android expose the session as logged in.

## Mobile SMS Flow
1. User enters mobile number.
2. Pending direct port: call `RawLoginApi.SendSmsCodeAsync(mobile)`.
3. On `Status == 1`, start local countdown and allow code entry.
4. User submits code through `RawLoginApi.LoginByMobileAsync(mobile, code)`.
5. On `Status == 1`, refresh token if needed, then enter Kugou content.
6. On failure, keep user on login page and show failed state.

## Session Cache
- Android cache payload:
  - `user_id`
  - `token`
  - `nickname`
  - `saved_at_ms`
  - `dfid`
  - `mid`
  - `uuid`
  - `install_guid`
  - `install_mac`
  - `install_dev`
  - `vip_type`
  - `t1`
  - `validation_state`
  - `validation_reason`
  - `validated_at_ms`
  - legacy `kugou_webapi_base_url` / `kugou_session_key` cache must be cleared on startup.
- Cache behavior:
  - startup: clear legacy WebApi state and restore direct session if `userid/token` exist.
  - pending startup: re-run validation before showing logged-in content.
  - success: persist refreshed token/device fields and enter default Kugou page.
  - failure: keep a blocked validation reason and show login/refresh action.
- Do not log raw token, `t1`, phone number or private crypto material.

## Content Gate
- Recommended songs, recommended radio and discover playlists are login-gated in Android S5 even if individual `.NET` clients can send `uid=0`.
- If not authenticated:
  - show login state instead of loading lists
  - do not issue content requests in the background
- If session becomes invalid while browsing:
  - clear local login state
  - navigate to login
  - preserve selected left-nav entry so it can retry after login

## Error Handling
- `KgApiControllerExtensions.FromKgStatus(...)` returns HTTP 200 when result status is null or `1`; otherwise HTTP 400 with the original model.
- Android must parse both:
  - HTTP status
  - model `status` / `error_code`
- Network failure, timeout, invalid JSON or empty body should not leave the page in a loading state.
- QR polling errors should be recoverable by refresh QR, not app restart.

## Validation Checklist
- Cold start with no direct session: default Kugou route shows login state and can refresh QR.
- Settings page does not expose Kugou WebApi Base URL.
- QR login button calls direct raw QR endpoint and starts 2s polling.
- QR success triggers direct device register and token refresh before content is unlocked.
- SMS login buttons do not issue legacy WebApi proxy requests; SMS remains pending AES/RSA direct port.
- Relaunch clears legacy WebApi cache.
- Logout clears local session state and returns to login panel.

## Non-Goals
- No user-configured WebApi Base URL product path.
- No raw protocol port in `T-S5-KG-109`; direct port must be a follow-up task.
- No account management beyond login/logout/session refresh.
