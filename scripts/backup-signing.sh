#!/usr/bin/env bash
# Encrypted backup of the release signing key:  scripts/backup-signing.sh [target file]
#
# Packs release.jks and keystore.properties (which holds the password) into one file encrypted with
# gpg (AES-256). gpg asks for a passphrase — keep it in your password manager. Then store the file in
# two places, e.g. Google Drive and a USB stick. Restoring is described in the README.
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f keystore.properties ]]; then
  echo "keystore.properties is missing — run scripts/setup-signing.sh first" >&2
  exit 1
fi
store="$(grep -E '^storeFile=' keystore.properties | cut -d= -f2-)"
out="${1:-$HOME/pullups-signaturschluessel-$(date +%F).tar.gpg}"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cp "$store" "$tmp/release.jks"
cp keystore.properties "$tmp/keystore.properties"

tar -C "$tmp" -cf - release.jks keystore.properties | gpg --symmetric --cipher-algo AES256 --output "$out"

echo "Backup written: $out"
echo "Check it with:  gpg -d \"$out\" | tar -tvf -"
