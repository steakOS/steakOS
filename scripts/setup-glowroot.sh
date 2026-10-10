#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Download optional local APM agent into the private companion tree.
# Not required for daily steakOS operations.
# Opt-in: ./gradlew bootRun -Pglowroot
# Default install path: ../steakOS-dev/tools/glowroot/
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${GLOWROOT_VERSION:-0.14.7}"
COMPANION="${STEAKOS_DEV_ROOT:-$ROOT/../steakOS-dev}"
DEST="${GLOWROOT_HOME:-$COMPANION/tools/glowroot}"
ZIP_URL="${GLOWROOT_URL:-https://github.com/glowroot/glowroot/releases/download/v${VERSION}/glowroot-${VERSION}-dist.zip}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$(dirname "$DEST")"

if [[ -f "$DEST/glowroot.jar" ]]; then
  echo "APM agent already present: $DEST/glowroot.jar"
  echo "  UI (after bootRun -Pglowroot): http://localhost:4000"
  exit 0
fi

echo "Downloading optional APM agent ${VERSION}…"
echo "  → $DEST"
curl -fsSL -o "$TMP/glowroot.zip" "$ZIP_URL"

echo "Extracting to $DEST…"
rm -rf "$DEST"
mkdir -p "$DEST"
unzip -q -o "$TMP/glowroot.zip" -d "$TMP/extract"
SHIM="$(find "$TMP/extract" -maxdepth 2 -type f -name 'glowroot.jar' | head -1)"
if [[ -z "$SHIM" ]]; then
  echo "ERROR: glowroot.jar not found in zip" >&2
  find "$TMP/extract" -maxdepth 3 -type f | head -40 >&2
  exit 1
fi
SRC_DIR="$(dirname "$SHIM")"
cp -a "$SRC_DIR"/. "$DEST"/

echo "OK: $DEST/glowroot.jar"
echo
echo "Run API with optional APM:"
echo "  ./gradlew bootRun -Pglowroot"
echo "Then open: http://localhost:4000"
echo "(API remains on http://localhost:6060)"
