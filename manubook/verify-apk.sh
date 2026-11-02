#!/usr/bin/env bash
# Checks a signed Manubook SMS APK: signed with APK signature scheme v2 or v3,
# and its signer certificate's SHA-256 equals the one in the given file
# (upper-case hex, colon-separated). Used by the release workflow, and by CI
# with a throwaway key so the same steps run on every PR.
# Usage: manubook/verify-apk.sh <apk> <expected-certificate-file>
set -euo pipefail

apk=$1
expected=$(cat "$2")
apksigner=$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)apksigner

"$apksigner" verify --verbose "$apk" | grep -E "Verified using v(2|3) scheme.*: true"
# apksigner names signers "Signer #1" or, in newer build-tools, per scheme
# ("V3.0 Signer:"), so match any signer line; every signer must carry the
# same certificate.
certs=$("$apksigner" verify --print-certs "$apk")
actual=$(printf '%s\n' "$certs" \
  | sed -n 's/^.*Signer.* certificate SHA-256 digest: //p' | sort -u \
  | tr 'a-f' 'A-F' | sed 's/../&:/g; s/:$//')
# Certificate fingerprints are public (the release one is committed), so a
# mismatch prints both.
test -n "$actual" && test "$actual" = "$expected" || {
  echo "signed with an unexpected certificate: got '$actual', expected '$expected'"
  test -n "$actual" || printf 'apksigner printed:\n%s\n' "$certs"
  exit 1
}
