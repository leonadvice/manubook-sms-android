# Manubook SMS: design note

Manubook SMS forwards the bank SMS a family phone receives to its Manubook organization. It is a thin product flavor over upstream's engine: upstream's manifest receiver catches each SMS, a WorkManager job posts it with retries. Our layer writes upstream's model objects and adds the setup screen and the heartbeat.

## What the flavor changes

- **Identity:** application ID `online.manubook.sms`, name *Manubook SMS*, Manubook icon, Vietnamese only. Java packages and the namespace stay upstream's, so merges do not conflict.
- **SDK:** `minSdk 26`, `targetSdk 35`. `compileSdk 35` is global. The foreground service declares type `specialUse`, which Android 15 still lets start from `BOOT_COMPLETED`.
- **Manifest** (`app/src/manubook/AndroidManifest.xml`):
  - no backup and no device transfer (`allowBackup=false`, `fullBackupContent=false`, data extraction rules excluding every domain), so the key never leaves the phone; a new phone gets a new code;
  - HTTPS only, system trust store, no pinning;
  - upstream's service and screens are not exported; `SetupActivity` is the launcher. Upstream's rule list, export and log viewer are never opened.
  - no `REQUEST_INSTALL_PACKAGES`: updates are installed by USB or a browser download, never by the app.
- **One forwarding rule**, written at setup: any sender; the text filter `(?s)TK (<n1>|<n2>)\b.*(So du|SD:)` built from the account numbers typed in (digits only, so it always compiles); the batch route `…/api/sms/batch` with the body template `{"messages":[{"text":"%text%","receivedAt":"%receivedStamp=yyyy-MM-dd'T'HH:mm:ss.SSSXXX%"}]}`, so every SMS is a one-item batch with its receive time; `Authorization: Bearer <key>`; 30 retries (about 4 days); failed messages kept; no SSL bypass.
- **Heartbeat owned by the flavor.** Upstream's heartbeat is off: its `Handler` timer stops in deep sleep. An inexact `setAndAllowWhileIdle` alarm fires every 30 minutes and is re-armed at each fire; a failed post retries after 1, 5 and 15 minutes through WorkManager; an hourly watchdog re-arms a lost alarm; reboot, app update and clock changes re-arm it. The body is the status of [CONTRACT.md](CONTRACT.md) §1.
- **Engine patches:** 4xx is final except 408 and 429 (a revoked key no longer retries for hours); logs hold no URL or header; the last delivery result is kept for the status.

## Scope by PR

- **W6a (`v0.1.0`; `v0.1.1` is the same build with a higher version, for the update rehearsal):** the flavor, manifest, patches, the minimal setup screen (scan or paste the setup code, account numbers, SMS and notification permissions), the beat with the fields `app`, `version`, `build`, `os`, `osBuild`, `patch`, `sms`, `notify`, `battery`, `bgRestricted`, `bucket`, `pending`, `failed`, `lastError`, `lastErrorAt`, CI and the signed release. It exists for the owner's install probe on their own phone.
- **W6b:** the setup checklist and status screen (battery, never-sleeping list, unused-app pause), *Gửi thử*, the phone-side notice when a setting breaks forwarding, and the beat fields `unusedExempt`, `lastSmsAt` and `smsOff`; resending failed messages after a new key.

## Known limits

- Untested on real phones until the owner's probe: Samsung Auto Blocker, Play Protect's fraud protection for `RECEIVE_SMS`, *Allow restricted settings* on Android 15+, real beat spacing in Doze.
- A bank notice sent by RCS or an app push never reaches an SMS app.
- The SMS receive time is formatted by upstream with the phone's default locale. A locale with non-ASCII digits (Arabic, Persian, Burmese) would send a `receivedAt` the server refuses; Vietnamese and English are fine.
