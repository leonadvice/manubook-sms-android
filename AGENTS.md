# Manubook SMS fork: how to work here

This repository is `leonadvice/manubook-sms-android`, a public fork of `bogkonstantin/android_income_sms_gateway_webhook`. It builds **Manubook SMS** (`online.manubook.sms`), which forwards bank SMS to Manubook. Read this file first; upstream's [CLAUDE.md](CLAUDE.md) describes the shared engine.

## Shape of the fork

- Everything Manubook lives in the `manubook` product flavor: [app/manubook.gradle](app/manubook.gradle), `app/src/manubook/` and `app/src/testManubook/`. The `upstream` flavor still builds the original app, so CI keeps proving the engine.
- Upstream files changed, each kept small and offered upstream as its own PR:
  - `app/build.gradle`: `compileSdkVersion 35` and `apply from: 'manubook.gradle'`;
  - `Request.java`, `RequestWorker.java`, new `DeliveryResult.java`: 4xx is final except 408 and 429; logs carry no URL, header or exception message that could hold a secret; the last delivery result is recorded;
  - `LICENSE.txt` (our copyright line), this pointer in `CLAUDE.md`.
- Never edit `.github/workflows/tests.yml`: it stays byte-identical so upstream merges never conflict on it. It is disabled in this fork (owner decision, 2026-11-02) because the flavors rename the Gradle tasks it calls; do not re-enable it. Our CI is `manubook-ci.yml`.
- Design note and contracts: [manubook/README.md](manubook/README.md), [manubook/CONTRACT.md](manubook/CONTRACT.md).

## Rules

- **Public repository: synthetic data only.** Account numbers, SMS text, keys and tokens in code, tests, fixtures, issues and PRs are made up. Never paste a real key, token, keystore value, account number or SMS.
- **No secret in logs.** Our code logs fixed event names and HTTP codes only: never a key, token, URL path, SMS text or account number.
- **One current shape.** Our flavor code keeps no old-format readers or version labels. Upstream's own rule to keep old stored entries readable applies to upstream's code only.
- **Upstream merges:** `git fetch upstream && git merge upstream/master` on a local branch, then a PR. Never rebase published history and never use GitHub's web "Sync fork" or any other commit made on GitHub.
- **PRs:** branch `claude/…` or `codex/…`, PR into this fork's `master` (never into bogkonstantin's repository), automatic `claude -p` review as in Manubook's `docs/operations/automatic-review.md`, merge after review approval and green `Manubook CI`.
- **Releases:** bump `versionCode` and `versionName` in `app/manubook.gradle`, merge, then push tag `v<versionName>` on `master`. `manubook-release.yml` builds, signs, checks the certificate against [manubook/signing-cert-sha256.txt](manubook/signing-cert-sha256.txt) and publishes the APK and its `.sha256`. Versions below 1.0.0 are pre-releases. The release environment waits for the owner's approval.
- **Signing key:** one key forever; Android refuses an update signed by another key. The keystore and its password are outside this repository (Manubook `docs/operations/access.md` names where). Secrets are set with [manubook/set-release-secrets.sh](manubook/set-release-secrets.sh) by the owner.
- **The Mac runs one heavy process at a time** (Gradle, emulator, a push). Prefer CI for full runs; run focused Gradle tasks locally.
- Physical-phone steps belong to the owner. Never change a phone's or the Mac's system settings.

## Build locally

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew testManubookDebugUnitTest assembleManubookDebug
```
