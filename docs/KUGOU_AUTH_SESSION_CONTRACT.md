# Kugou Auth And Session Contract

Last Updated: 2026-06-03

## Scope
- Covers S5 Android login/session behavior for Kugou mode.
- Source of truth: `docs/KUGOU_MUSIC_NET_INTERFACE_MAP.md` and `KugouMusic.NET/`.
- Default login method: QR code.
- Secondary login method: mobile SMS code.

## Session Transport
- WebApi session identity is `X-Kg-Session-Id` header or `kg_sid` cookie.
- Source:
  - `KgWebSessionMiddleware.cs`
  - `KgWebSessionContext.cs`
  - `KgWebSessionPersistence.cs`
- Android should persist the session key returned by WebApi and attach it to all Kugou requests.
- Session key is not the Kugou token. The Kugou token is stored server-side in WebApi session persistence.

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
  - no cached WebApi session key
  - refresh token result has `Status != 1`
  - any content endpoint returns a failed `KgBaseModel` status that corresponds to auth/session failure
  - WebApi session reset or logout

## QR Login Flow
1. Android opens Kugou login page/dialog in QR mode.
2. Call `GET /login/qr/key`.
3. Show `QRCode.QrcodeImg`; keep `QRCode.Qrcode` as poll key.
4. Poll `GET /login/qr/check?key=...` every 2 seconds.
5. State mapping from `.NET LoginViewModel`:
   - `WaitingForScan`: show waiting scan state.
   - `WaitingForConfirm`: show scanned, waiting phone confirmation.
   - `Success`: stop polling, call `POST /login/token` to refresh session, then enter Kugou content.
   - `Expired`: stop polling and show refresh QR action.
6. On page leave or login mode switch, stop polling.

## Mobile SMS Flow
1. User enters mobile number.
2. Call `POST /captcha/sent?mobile=...`.
3. On `Status == 1`, start local countdown and allow code entry.
4. User submits code via `POST /login/cellphone` body `{ mobile, code }`.
5. On `Status == 1`, call `POST /login/token` if needed, then enter Kugou content.
6. On failure, keep user on login page and show failed state.

## Session Cache
- Android cache payload:
  - `kugou_webapi_base_url`
  - `kugou_session_key`
  - `saved_at_ms`
  - optional `last_user_id` / display name when available
- Cache behavior:
  - startup: if base URL and session key exist, attach session key and call `POST /login/token` or a light authenticated status endpoint.
  - success: keep session key and enter default Kugou page.
  - failure: clear cached session key and show login.
- Do not cache raw Kugou token on Android when using WebApi session mode.

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
- Cold start with no session: default Kugou recommended songs route shows QR login.
- QR key loads and image appears.
- QR expired state stops polling and shows refresh action.
- SMS send rejects invalid mobile before request.
- SMS login success persists session key.
- Relaunch reuses cached session until refresh/content request fails.
- Logout clears session key and returns to login.

## Non-Goals
- No direct Android storage of raw Kugou token in WebApi mode.
- No raw protocol port in this task.
- No account management beyond login/logout/session refresh.
