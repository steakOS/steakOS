#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The steakOS Authors
#
# Install / place nostr-rs-relay for steakOS (OI-NOSTR01 / D1–D5, D2 native binary).
#
# Usage:
#   ./scripts/install/nostr/install-nostr-relay.sh              # project-local var/nostr (dev)
#   ./scripts/install/nostr/install-nostr-relay.sh --system     # /var/lib/steakos/nostr (needs root)
#   ./scripts/install/nostr/install-nostr-relay.sh --prefix "$HOME/steakOS"
#   ./scripts/install/nostr/install-nostr-relay.sh --skip-download  # config + unit only
#
# Environment:
#   STEAKOS_NOSTR_PORT   listen port (default 7777)
#   STEAKOS_NOSTR_VERSION  optional release tag (e.g. 0.9.0) — when unset, uses cargo/docker fallback notes
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
PORT="${STEAKOS_NOSTR_PORT:-7777}"
SYSTEM=0
PREFIX=""
SKIP_DOWNLOAD=0
START=0

usage() {
  sed -n '2,20p' "$0"
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --system) SYSTEM=1; shift ;;
    --prefix) PREFIX="${2:-}"; shift 2 ;;
    --port) PORT="${2:-}"; shift 2 ;;
    --skip-download) SKIP_DOWNLOAD=1; shift ;;
    --start) START=1; shift ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
done

die() { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*"; }

OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64|amd64) ARCH_NORM="x86_64" ;;
  aarch64|arm64) ARCH_NORM="aarch64" ;;
  *) ARCH_NORM="$ARCH" ;;
esac

if [[ "$SYSTEM" -eq 1 ]]; then
  [[ "$(id -u)" -eq 0 ]] || die "--system requires root"
  DATA_DIR="/var/lib/steakos/nostr"
  BIN_DIR="/usr/local/bin"
  UNIT_DST="/etc/systemd/system/steakos-nostr-relay.service"
else
  if [[ -n "$PREFIX" ]]; then
    DATA_DIR="$PREFIX/var/nostr"
    BIN_DIR="$PREFIX/bin"
  else
    DATA_DIR="$REPO_ROOT/var/nostr"
    BIN_DIR="$REPO_ROOT/var/nostr/bin"
  fi
  UNIT_DST=""
fi

mkdir -p "$DATA_DIR" "$BIN_DIR"
info "Data dir: $DATA_DIR"
info "Binary dir: $BIN_DIR"
info "Port: $PORT (loopback 127.0.0.1 — D3)"

# Seed config
if [[ ! -f "$DATA_DIR/config.toml" ]]; then
  sed "s/port = 7777/port = ${PORT}/; s|ws://127.0.0.1:7777/|ws://127.0.0.1:${PORT}/|; s|data_directory = \".\"|data_directory = \"${DATA_DIR}\"|" \
    "$SCRIPT_DIR/config.toml" > "$DATA_DIR/config.toml"
  info "Wrote $DATA_DIR/config.toml"
else
  info "Keeping existing $DATA_DIR/config.toml"
fi

RELAY_URL="ws://127.0.0.1:${PORT}"
echo "$RELAY_URL" > "$DATA_DIR/relay.url"
info "Seed relay URL: $RELAY_URL (persist into company settings via Integrations / wizard)"

BINARY="$BIN_DIR/nostr-rs-relay"
if [[ "$SKIP_DOWNLOAD" -eq 0 ]]; then
  if command -v nostr-rs-relay >/dev/null 2>&1; then
    info "nostr-rs-relay already on PATH: $(command -v nostr-rs-relay)"
    if [[ ! -e "$BINARY" ]]; then
      ln -sf "$(command -v nostr-rs-relay)" "$BINARY" || cp "$(command -v nostr-rs-relay)" "$BINARY"
    fi
  elif [[ -x "$BINARY" ]]; then
    info "Using existing $BINARY"
  else
    info "Downloading / building nostr-rs-relay is host-specific."
    info "Preferred: place a release binary at $BINARY"
    info "  Source: https://github.com/scsibug/nostr-rs-relay (SourceHut master + GitHub mirror)"
    info "  Docker fallback (ops only, not D2 default): docker pull scsibug/nostr-rs-relay"
    if command -v cargo >/dev/null 2>&1; then
      info "cargo found — attempting cargo install nostr-rs-relay into $BIN_DIR"
      CARGO_HOME="${CARGO_HOME:-$HOME/.cargo}"
      if [[ -f "${HOME}/.local/opt/openssl-dev/usr/include/openssl/ssl.h" ]]; then
        export OPENSSL_DIR="${HOME}/.local/opt/openssl-dev/usr"
        export OPENSSL_INCLUDE_DIR="${OPENSSL_DIR}/include"
        export OPENSSL_LIB_DIR="${OPENSSL_DIR}/lib/x86_64-linux-gnu"
        export PKG_CONFIG_PATH="${OPENSSL_LIB_DIR}/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
      fi
      if cargo install --git https://github.com/scsibug/nostr-rs-relay nostr-rs-relay \
            --root "$DATA_DIR/cargo-root" 2>/tmp/steakos-nostr-cargo.log \
         || cargo install nostr-rs-relay --root "$DATA_DIR/cargo-root" 2>>/tmp/steakos-nostr-cargo.log; then
        cp "$DATA_DIR/cargo-root/bin/nostr-rs-relay" "$BINARY"
        chmod +x "$BINARY"
        info "Installed via cargo → $BINARY"
      else
        info "cargo install failed (see /tmp/steakos-nostr-cargo.log). Leaving placeholder."
        cat > "$DATA_DIR/INSTALL-RELAY.txt" <<EOF
Place nostr-rs-relay binary at:
  $BINARY
Then start:
  $BINARY --config $DATA_DIR/config.toml --db $DATA_DIR
Or on Linux with --system: systemctl enable --now steakos-nostr-relay
EOF
      fi
    else
      cat > "$DATA_DIR/INSTALL-RELAY.txt" <<EOF
nostr-rs-relay binary not found.
1. Build or download for ${OS}/${ARCH_NORM}
2. Install to: $BINARY
3. Start: $BINARY --config $DATA_DIR/config.toml --db $DATA_DIR
See docs/installation/QUICKSTART.md § Nostr relay and OPERATOR-RUNBOOK.md.
EOF
      info "Wrote $DATA_DIR/INSTALL-RELAY.txt — binary not placed (honest residual until artifact is vendored)."
    fi
  fi
fi

# systemd / launchd templates
if [[ "$SYSTEM" -eq 1 && -d /etc/systemd/system ]]; then
  sed "s|/var/lib/steakos/nostr|${DATA_DIR}|g; s|/usr/local/bin/nostr-rs-relay|${BINARY}|g" \
    "$SCRIPT_DIR/steakos-nostr-relay.service" > "$UNIT_DST"
  systemctl daemon-reload || true
  info "Installed systemd unit: $UNIT_DST"
  if [[ "$START" -eq 1 && -x "$BINARY" ]]; then
    systemctl enable --now steakos-nostr-relay || die "failed to start steakos-nostr-relay"
  fi
elif [[ "$OS" == "darwin" ]]; then
  PLIST_SRC="$SCRIPT_DIR/com.steakos.nostr-relay.plist"
  PLIST_DST="${PREFIX:-$HOME}/Library/LaunchAgents/com.steakos.nostr-relay.plist"
  mkdir -p "$(dirname "$PLIST_DST")"
  # Rewrite paths into a working copy under data dir
  sed "s|/usr/local/bin/nostr-rs-relay|${BINARY}|g; s|/usr/local/var/steakos/nostr|${DATA_DIR}|g" \
    "$PLIST_SRC" > "$DATA_DIR/com.steakos.nostr-relay.plist"
  info "launchd plist template: $DATA_DIR/com.steakos.nostr-relay.plist"
  info "Load with: launchctl load $DATA_DIR/com.steakos.nostr-relay.plist"
fi

# Dev convenience start script
cat > "$DATA_DIR/start-relay.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
BIN="${BINARY}"
CFG="${DATA_DIR}/config.toml"
DB="${DATA_DIR}"
if [[ ! -x "\$BIN" ]]; then
  echo "Missing binary: \$BIN" >&2
  echo "See ${DATA_DIR}/INSTALL-RELAY.txt" >&2
  exit 1
fi
exec "\$BIN" --config "\$CFG" --db "\$DB"
EOF
chmod +x "$DATA_DIR/start-relay.sh"

if [[ "$START" -eq 1 && "$SYSTEM" -eq 0 && -x "$BINARY" ]]; then
  info "Starting relay in background (dev)…"
  nohup "$BINARY" --config "$DATA_DIR/config.toml" --db "$DATA_DIR" \
    >"$DATA_DIR/relay.out.log" 2>"$DATA_DIR/relay.err.log" &
  echo $! > "$DATA_DIR/relay.pid"
  info "PID $(cat "$DATA_DIR/relay.pid") — logs in $DATA_DIR/relay.*.log"
fi

info "Done. Seed steakOS setting nostr.relayUrl=${RELAY_URL}"
info "Backup note (D5): include $DATA_DIR (SQLite + config) in operator backups."
