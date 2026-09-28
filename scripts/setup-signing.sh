#!/usr/bin/env bash
# One-time setup: creates the release signing key and stores it as GitHub secrets for the
# release workflow.
#
# Every release must be signed with this same key — Android refuses updates signed with a
# different key, and the app would then have to be uninstalled (= all data lost).
# => Back up the key and its password (password manager + offline copy)!
set -euo pipefail

cd "$(dirname "$0")/.."

dir="${KEYSTORE_DIR:-$HOME/.android/trainingtracker}"
store="$dir/release.jks"
alias="pullups"
dname="${KEY_DNAME:-CN=Pull-ups Release}"

if [[ -e "$store" || -e keystore.properties ]]; then
  echo "A release key already exists ($store / keystore.properties) — nothing was changed." >&2
  echo "Only upload it to GitHub again:  $0 --upload" >&2
  [[ "${1:-}" == "--upload" ]] || exit 1
else
  mkdir -p "$dir"
  chmod 700 "$dir"
  export KEY_PASSWORD
  KEY_PASSWORD="$(openssl rand -hex 24)"
  keytool -genkeypair -keystore "$store" -storetype PKCS12 -alias "$alias" \
    -keyalg RSA -keysize 4096 -validity 36500 -dname "$dname" \
    -storepass:env KEY_PASSWORD -keypass:env KEY_PASSWORD
  chmod 600 "$store"
  umask 077
  cat > keystore.properties <<EOF
storeFile=$store
storePassword=$KEY_PASSWORD
keyAlias=$alias
keyPassword=$KEY_PASSWORD
EOF
  echo "Created $store and keystore.properties (both are gitignored)."
fi

password="$(grep -E '^storePassword=' keystore.properties | cut -d= -f2-)"
base64 -w0 "$(grep -E '^storeFile=' keystore.properties | cut -d= -f2-)" | gh secret set RELEASE_KEYSTORE_BASE64
printf '%s' "$password" | gh secret set RELEASE_STORE_PASSWORD
printf '%s' "$alias" | gh secret set RELEASE_KEY_ALIAS
printf '%s' "$password" | gh secret set RELEASE_KEY_PASSWORD
echo "GitHub secrets set."
echo
echo "IMPORTANT: back up the key now:  scripts/backup-signing.sh"
echo "Without it no further updates can be published for installed apps."
