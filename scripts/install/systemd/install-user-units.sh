#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The steakOS Authors
#
# Install packaged systemd --user units for steakOS API (:6060) + Vite (:9001).
# OI-V06 / A6 option C residual — units ship in-repo; enabling is optional.
#
# Usage:
#   ./scripts/install/systemd/install-user-units.sh              # install only
#   ./scripts/install/systemd/install-user-units.sh --enable     # install + enable --now
#   ./scripts/install/systemd/install-user-units.sh --dry-run
#   STEAKOS_ROOT=/path/to/steakOS ./scripts/install/systemd/install-user-units.sh
#
# Environment:
#   STEAKOS_ROOT   app tree (default: repo containing this script)
#   UNIT_DIR       override unit destination (default: ~/.config/systemd/user)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
STEAKOS_ROOT="${STEAKOS_ROOT:-$REPO_ROOT}"
UNIT_DIR="${UNIT_DIR:-$HOME/.config/systemd/user}"
ENABLE=0
DRY=0

usage() {
  sed -n '2,18p' "$0"
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --enable|--enable-now) ENABLE=1; shift ;;
    --dry-run) DRY=1; shift ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
done

die() { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*"; }

[[ -d "$STEAKOS_ROOT" ]] || die "STEAKOS_ROOT not a directory: $STEAKOS_ROOT"
[[ -x "$SCRIPT_DIR/steakos-api-start.sh" ]] || chmod +x "$SCRIPT_DIR"/steakos-*-start.sh
[[ -x "$SCRIPT_DIR/steakos-vite-start.sh" ]] || chmod +x "$SCRIPT_DIR"/steakos-*-start.sh

render() {
  local src="$1" dst="$2"
  sed "s|@STEAKOS_ROOT@|${STEAKOS_ROOT}|g" "$src"
}

info "STEAKOS_ROOT=$STEAKOS_ROOT"
info "UNIT_DIR=$UNIT_DIR"

if [[ "$DRY" -eq 1 ]]; then
  info "dry-run: would write:"
  echo "  $UNIT_DIR/steakos-api.service"
  echo "  $UNIT_DIR/steakos-vite.service"
  render "$SCRIPT_DIR/steakos-api.service" /dev/null | head -20
  echo "…"
  exit 0
fi

mkdir -p "$UNIT_DIR" "$HOME/.config/steakos"
render "$SCRIPT_DIR/steakos-api.service" "$UNIT_DIR/steakos-api.service" \
  >"$UNIT_DIR/steakos-api.service"
render "$SCRIPT_DIR/steakos-vite.service" "$UNIT_DIR/steakos-vite.service" \
  >"$UNIT_DIR/steakos-vite.service"
info "Wrote $UNIT_DIR/steakos-api.service"
info "Wrote $UNIT_DIR/steakos-vite.service"

if ! command -v systemctl >/dev/null 2>&1; then
  info "systemctl not found — units installed as files only"
  exit 0
fi

systemctl --user daemon-reload
info "daemon-reload done"

if [[ "$ENABLE" -eq 1 ]]; then
  shopt -s nullglob
  jars=("$STEAKOS_ROOT"/build/libs/steakOS.jar "$STEAKOS_ROOT"/build/libs/steakOS-*.jar)
  shopt -u nullglob
  if [[ ${#jars[@]} -eq 0 ]]; then
    die "no bootJar found — run: cd \"$STEAKOS_ROOT\" && ./gradlew bootJar"
  fi
  systemctl --user enable --now steakos-api.service steakos-vite.service
  info "enabled --now steakos-api steakos-vite"
  systemctl --user --no-pager --full status steakos-api.service steakos-vite.service || true
else
  info "Units installed but not enabled."
  info "Enable when ready:"
  echo "  systemctl --user enable --now steakos-api.service steakos-vite.service"
  echo "  loginctl enable-linger \"\$USER\"   # keep after logout"
  echo "  journalctl --user -u steakos-api -u steakos-vite -f"
fi
