# Manubook phone contracts (copy)

Copied from Manubook `main` at `e88506a5` (2026-11-02): `openspec/changes/bank-sms-import/design.md` decision 32 and `apps/api/src/bank-sms/beat-status.ts` for §1, the receiver contract for §2, the setup code for §3. Where Manubook changes these, this copy is updated in the same fork PR that codes the change. The unit tests in `app/src/testManubook/` check the app against this copy.

## 1. Heartbeat with status: `POST /api/sms/beat/{token}`

- Token: 43 base64url characters in the path. Bad, unknown or revoked: `401`. 30 a minute per token, then `429`.
- Body at most 1,024 bytes (`413` above). Empty body = a sign of life with no status. Any other body is read as JSON whatever its content type.
- A live token always answers **`204`**. One bad field makes the whole status unreadable (recorded, never refused), so the app leaves out an optional field that fails its pattern.

| Key | Required | Server rule |
|---|---|---|
| `app` | yes | `"android"` |
| `version` | yes | `^[0-9A-Za-z.+-]{1,32}$` (`versionName`) |
| `build` | yes | integer 1–2147483647 (`versionCode`) |
| `os` | yes | `^[0-9A-Za-z .+_-]{1,32}$` (`Build.VERSION.RELEASE`) |
| `osBuild` | no | `^[0-9A-Za-z .+_(),/-]{1,64}$` (`Build.DISPLAY`) |
| `patch` | no | `YYYY-MM-DD`, a real day (`Build.VERSION.SECURITY_PATCH`) |
| `sms`, `notify`, `battery`, `unusedExempt`, `bgRestricted` | no | boolean |
| `bucket` | no | integer 5–50 |
| `pending` | no | integer 0–100000: a snapshot of delivery jobs ENQUEUED, RUNNING or BLOCKED |
| `failed` | no | integer 0–100000: SMS given up since the last beat that answered 204 |
| `lastError` | no | `^[0-9A-Za-z_.:-]{1,40}$`: `HTTP_<status>`, `NETWORK`, `TIMEOUT`, `TLS` |
| `lastErrorAt` | only with `lastError` | instant |
| `lastSmsAt` | no | instant |
| `smsOff` | no | at most 5 `{from, to}`, oldest first; `to` null or absent while still off |

- Instant: `^20\d{2}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-](0\d|1[0-5]):[0-5]\d)$`.
- An unknown key makes the status unreadable. `null` and absent both mean unknown; the app leaves the field out.
- Beat cadence: every 30 minutes. The server checks for a sign of life in the hour before 09:30, 14:30 and 20:30.

## 2. SMS route: `POST /api/sms/batch`

- `Authorization: Bearer <43 base64url>`, `Content-Type: application/json`.
- Body exactly `{"messages":[{"text":"<SMS>","receivedAt":"<instant>"}]}`; the app always sends one item.
- `202` with one result per item (`stored`, `duplicate`, `refused`, `later`). Resending is always safe: the same text from the same phone answers `duplicate`.
- `400`, `401`, `413` are final on the phone. `408`, `429`, `5xx` and network errors retry. A request whose every item is `later` (500 a day) answers `429` once Manubook's W3 change is live.

## 3. Setup code (QR or paste)

Exactly three lines joined by `\n`, no trailing line feed:

```
https://api.manubook.online/api/sms
Bearer <43 base64url>
https://api.manubook.online/api/sms/beat/<43 base64url>
```

The batch address is line 1 plus `/batch`. The release build refuses any other host; a debug build also accepts another HTTPS host, the same on both address lines. A fourth value would mean every phone re-scans; no version label is ever added.
