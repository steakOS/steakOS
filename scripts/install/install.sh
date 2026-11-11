#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The steakOS Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# steakOS one-command installer (Linux / macOS / WSL).
#
# Fetch a pack-dist archive from a static host (LAN or later GitHub Releases)
# and install + optionally start it.
#
# Usage:
#   curl -fsSL http://steakos.lan:8088/install.sh | bash
#   curl -fsSL http://steakos.lan:8088/install.sh | bash -s -- --base-url http://steakos.lan:8088
#   ./install.sh --base-url http://192.168.1.10:8088 --prefix "$HOME/steakOS"
#   ./install.sh --offline /path/to/steakOS-V0.1.6-….zip
#   ./install.sh --base-url … --no-start
#
# Environment:
#   STEAKOS_BASE_URL   default artifact host (same as --base-url)
#   STEAKOS_PREFIX     install directory (default: $HOME/steakOS)
#   STEAKOS_VERSION    optional pin (matches latest.json softwareVersion or package name fragment)
set -euo pipefail

BASE_URL="${STEAKOS_BASE_URL:-}"
PREFIX="${STEAKOS_PREFIX:-${HOME}/steakOS}"
OFFLINE_ZIP=""
NO_START=0
VERSION_PIN="${STEAKOS_VERSION:-}"
ASSUME_YES=0

usage() {
  sed -n '2,20p' "$0"
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url|-u) BASE_URL="${2:-}"; shift 2 ;;
    --prefix|-p)   PREFIX="${2:-}"; shift 2 ;;
    --offline|-f)  OFFLINE_ZIP="${2:-}"; shift 2 ;;
    --version|-v)  VERSION_PIN="${2:-}"; shift 2 ;;
    --no-start)    NO_START=1; shift ;;
    -y|--yes)      ASSUME_YES=1; shift ;;
    -h|--help)     usage ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      ;;
  esac
done

die() { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*"; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

# --- resolve artifact ---------------------------------------------------------
TMPDIR_INSTALL="${TMPDIR:-/tmp}/steakos-install-$$"
mkdir -p "$TMPDIR_INSTALL"
cleanup() { rm -rf "$TMPDIR_INSTALL"; }
trap cleanup EXIT

ZIP_PATH=""
if [[ -n "$OFFLINE_ZIP" ]]; then
  [[ -f "$OFFLINE_ZIP" ]] || die "offline zip not found: $OFFLINE_ZIP"
  ZIP_PATH="$OFFLINE_ZIP"
  info "Using offline package $ZIP_PATH"
else
  [[ -n "$BASE_URL" ]] || die "set --base-url or STEAKOS_BASE_URL (or use --offline)"
  BASE_URL="${BASE_URL%/}"
  need_cmd curl

  META_URL="$BASE_URL/latest.json"
  info "Fetching $META_URL"
  PUBLIC_HOST=0
  if [[ "$BASE_URL" == *github.com* || "${STEAKOS_REQUIRE_CHECKSUM:-0}" == "1" ]]; then
    PUBLIC_HOST=1
  fi
  if curl -fsSL "$META_URL" -o "$TMPDIR_INSTALL/latest.json"; then
    ZIP_NAME="$(sed -n 's/.*"zip"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$TMPDIR_INSTALL/latest.json" | head -1)"
    PKG_VER="$(sed -n 's/.*"softwareVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$TMPDIR_INSTALL/latest.json" | head -1)"
    if [[ -z "$ZIP_NAME" ]]; then
      die "latest.json at $META_URL has no zip field. Refusing to guess latest.zip."
    fi
  else
    if [[ "$PUBLIC_HOST" -eq 1 ]]; then
      die "latest.json missing at $META_URL. Refusing to guess latest.zip."
    fi
    info "latest.json missing — trying latest.zip"
    ZIP_NAME="latest.zip"
    PKG_VER=""
  fi

  if [[ -n "$VERSION_PIN" && -n "$PKG_VER" && "$VERSION_PIN" != "$PKG_VER" ]]; then
    # try versioned name convention steakOS-V{ver}-*.zip via checksums listing is host-specific;
    # for pin, require explicit offline or host that still points latest at that ver.
    die "host latest is softwareVersion=$PKG_VER but --version $VERSION_PIN was requested (use matching host or --offline)"
  fi

  [[ -n "$ZIP_NAME" ]] || ZIP_NAME="latest.zip"
  ART_URL="$BASE_URL/$ZIP_NAME"
  ZIP_PATH="$TMPDIR_INSTALL/pkg.zip"
  info "Downloading $ART_URL"
  curl -fL --progress-bar "$ART_URL" -o "$ZIP_PATH"

  # checksum verification (required for public GitHub / STEAKOS_REQUIRE_CHECKSUM=1)
  REQUIRE_SUM="${STEAKOS_REQUIRE_CHECKSUM:-0}"
  if [[ "$BASE_URL" == *github.com* ]]; then
    REQUIRE_SUM=1
  fi
  if curl -fsSL "$BASE_URL/checksums.sha256" -o "$TMPDIR_INSTALL/checksums.sha256" 2>/dev/null; then
    info "Verifying checksum"
    if command -v sha256sum >/dev/null 2>&1; then
      (
        cd "$TMPDIR_INSTALL"
        # checksums file lists zip basename — rewrite to pkg.zip if needed
        if grep -q "$(basename "$ZIP_NAME")" checksums.sha256 2>/dev/null; then
          # copy under expected name
          cp -f pkg.zip "$ZIP_NAME" 2>/dev/null || true
          sha256sum -c checksums.sha256 --ignore-missing 2>/dev/null \
            || sha256sum -c <(grep "$ZIP_NAME" checksums.sha256) \
            || die "checksum mismatch"
        else
          if [[ "$REQUIRE_SUM" == "1" ]]; then
            die "checksums.sha256 has no entry for $ZIP_NAME (required on public host)"
          fi
          info "checksums.sha256 has no entry for $ZIP_NAME — skipping strict check"
        fi
      )
    elif command -v shasum >/dev/null 2>&1; then
      (
        cd "$TMPDIR_INSTALL"
        cp -f pkg.zip "$ZIP_NAME" 2>/dev/null || true
        EXPECT="$(grep "$(basename "$ZIP_NAME")" checksums.sha256 | awk '{print $1}')"
        GOT="$(shasum -a 256 pkg.zip | awk '{print $1}')"
        [[ -n "$EXPECT" && "$EXPECT" == "$GOT" ]] || die "checksum mismatch (shasum)"
      )
    else
      die "need sha256sum or shasum to verify download"
    fi
  else
    if [[ "$REQUIRE_SUM" == "1" ]]; then
      die "checksums.sha256 missing on $BASE_URL (required for GitHub / public installs)"
    fi
    info "No checksums.sha256 on host — skipping verify (OK for trusted LAN)"
  fi
fi

# --- extract ------------------------------------------------------------------
need_cmd java
JAVA_VER="$(java -version 2>&1 | head -1 || true)"
info "Java: $JAVA_VER"

EXTRACT="$TMPDIR_INSTALL/extract"
mkdir -p "$EXTRACT"
case "$ZIP_PATH" in
  *.tar.gz|*.tgz)
    need_cmd tar
    tar -xzf "$ZIP_PATH" -C "$EXTRACT"
    ;;
  *)
    if command -v unzip >/dev/null 2>&1; then
      unzip -q "$ZIP_PATH" -d "$EXTRACT"
    elif command -v jar >/dev/null 2>&1; then
      (cd "$EXTRACT" && jar xf "$ZIP_PATH")
    else
      die "need unzip (or jar) to extract package"
    fi
    ;;
esac

PKG_DIR="$(find "$EXTRACT" -mindepth 1 -maxdepth 1 -type d | head -1)"
[[ -n "$PKG_DIR" && -d "$PKG_DIR" ]] || die "package root not found inside archive"
[[ -f "$PKG_DIR/app/steakOS.jar" || -f "$PKG_DIR/bin/steakos" ]] || die "unexpected package layout in $PKG_DIR"

# --- install ------------------------------------------------------------------
info "Installing to $PREFIX"
mkdir -p "$PREFIX"
# rsync if available; else cp
if command -v rsync >/dev/null 2>&1; then
  rsync -a --delete --exclude 'config/steakos.env' --exclude 'run/' --exclude 'logs/' \
    "$PKG_DIR/" "$PREFIX/"
else
  # preserve user config if present
  if [[ -f "$PREFIX/config/steakos.env" ]]; then
    cp -a "$PREFIX/config/steakos.env" "$TMPDIR_INSTALL/steakos.env.bak"
  fi
  rm -rf "$PREFIX/app" "$PREFIX/web" "$PREFIX/bin" "$PREFIX/db"
  mkdir -p "$PREFIX"
  cp -a "$PKG_DIR"/. "$PREFIX/"
  if [[ -f "$TMPDIR_INSTALL/steakos.env.bak" ]]; then
    mkdir -p "$PREFIX/config"
    cp -a "$TMPDIR_INSTALL/steakos.env.bak" "$PREFIX/config/steakos.env"
  fi
fi

chmod +x "$PREFIX/bin/steakos" 2>/dev/null || true

if [[ ! -f "$PREFIX/config/steakos.env" ]]; then
  cp "$PREFIX/config/steakos.env.example" "$PREFIX/config/steakos.env"
  info "Wrote $PREFIX/config/steakos.env from example — edit DB password before production use"
fi

# --- optional start -----------------------------------------------------------
if [[ "$NO_START" -eq 0 ]]; then
  info "Starting steakOS"
  "$PREFIX/bin/steakos" start || {
    echo "warn: start failed — check Postgres and $PREFIX/logs/steakos.log" >&2
    echo "      DB bootstrap: psql … -f $PREFIX/db/setup-postgresql.sql" >&2
  }
else
  info "Skipping start (--no-start). Run: $PREFIX/bin/steakos start"
fi

echo ""
echo "steakOS installed"
echo "  prefix : $PREFIX"
if [[ -f "$PREFIX/VERSION" ]]; then
  echo "  version:"
  sed 's/^/    /' "$PREFIX/VERSION"
fi
echo "  control: $PREFIX/bin/steakos {start|stop|status|logs|version}"
echo "  config : $PREFIX/config/steakos.env"
echo "  docs   : operator docs in the private companion / release notes"
echo ""
echo "Next: ensure PostgreSQL databases exist (db/setup-postgresql.sql), then open the API URL printed by start."
