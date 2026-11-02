#!/bin/sh
# Sets the three release signing secrets in this fork's `release` environment.
# Run by the owner on the Mac that holds the keystore. Prints no secret value:
# the keystore is read from its file and the password is typed at a hidden prompt.
set -eu
repo=leonadvice/manubook-sms-android
keystore="$HOME/.config/manubook/android-signing/manubook-sms-release.jks"
export GH_CONFIG_DIR="${GH_CONFIG_DIR:-$HOME/.config/gh-personal}"

test -f "$keystore" || { echo "keystore not found: $keystore"; exit 1; }
base64 -i "$keystore" | tr -d '\n' | gh secret set MANUBOOK_KEYSTORE_B64 --repo "$repo" --env release
printf '%s' manubook-sms | gh secret set MANUBOOK_KEY_ALIAS --repo "$repo" --env release
trap 'stty echo' EXIT INT TERM
printf 'Keystore password (hidden): '
stty -echo
read -r password
stty echo
echo
printf '%s' "$password" | gh secret set MANUBOOK_KEYSTORE_PASSWORD --repo "$repo" --env release
unset password
echo "Done: three secrets set in the release environment."
