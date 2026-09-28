#!/usr/bin/env bash
# Publishes a new version:  scripts/release.sh 1.1.0 "Was ist neu"
#
# Bumps appVersionName in gradle.properties, runs the unit tests, commits, tags v1.1.0 and pushes.
# GitHub Actions (.github/workflows/release.yml) then builds the signed APK and publishes the
# release — installed apps find it on their next start.
set -euo pipefail

version="${1:?Usage: scripts/release.sh <MAJOR.MINOR.PATCH> [release notes]}"
notes="${2:-Version $version}"

cd "$(dirname "$0")/.."

if [[ ! "$version" =~ ^[0-9]{1,2}\.[0-9]{1,2}\.[0-9]{1,2}$ ]]; then
  echo "Version must look like MAJOR.MINOR.PATCH with each part 0-99, e.g. 1.2.0" >&2
  exit 1
fi

version_code() {
  local major minor patch
  IFS=. read -r major minor patch <<< "$1"
  echo $((10#$major * 10000 + 10#$minor * 100 + 10#$patch))
}

current=$(grep -E '^appVersionName=' gradle.properties | cut -d= -f2 | tr -d '[:space:]')
if (( $(version_code "$version") <= $(version_code "$current") )); then
  echo "New version $version must be higher than the current $current" >&2
  exit 1
fi
if [[ "$(git rev-parse --abbrev-ref HEAD)" != "main" ]]; then
  echo "Releases are made from main" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Please commit or stash your changes first" >&2
  exit 1
fi

sed -i.bak "s/^appVersionName=.*/appVersionName=$version/" gradle.properties && rm gradle.properties.bak
./gradlew testDebugUnitTest

git commit -am "Release v$version"
git tag -a "v$version" -m "$notes"
git push origin main "v$version"

echo
echo "v$version pushed. GitHub Actions builds and publishes it:"
echo "  $(gh repo view --json url -q .url 2>/dev/null || echo 'https://github.com/<repo>')/actions"
