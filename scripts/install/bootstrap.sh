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

# steakOS bootstrap (Linux / macOS / WSL) — Task one-line install P3.
# Ensures Java 21 + PostgreSQL 16 + DB roles, then runs the package installer.
#
# Usage:
#   curl -fsSL http://umbrel.local:8101/hermesbro/steakOS/raw/branch/main/scripts/install/bootstrap.sh | bash
#   ./bootstrap.sh --yes
#   ./bootstrap.sh --skip-deps --base-url http://umbrel.local:8101/hermesbro/steakOS/releases/latest/download
#   ./bootstrap.sh --offline /path/to/steakOS-….zip
#
# Environment:
#   STEAKOS_HUB_HOST / STEAKOS_HUB_OWNER / STEAKOS_HUB_REPO  (Forgejo defaults)
#   STEAKOS_BASE_URL     package host (default: hub releases/latest/download)
#   STEAKOS_RAW_BASE     raw file base for fetching install.sh when not local
#   STEAKOS_PREFIX       install dir (default: $HOME/steakOS)
#   STEAKOS_ASSUME_YES   1 = non-interactive yes
#   Future public GitHub: set STEAKOS_BASE_URL / STEAKOS_RAW_BASE to github.com/steakOS/steakOS
set -euo pipefail

# Default host: Forgejo hub on the office LAN (GitHub public mirror deferred).
# Override with STEAKOS_BASE_URL / STEAKOS_RAW_BASE when the public GitHub mirror is live.
HUB_HOST="${STEAKOS_HUB_HOST:-http://umbrel.local:8101}"
HUB_OWNER="${STEAKOS_HUB_OWNER:-hermesbro}"
HUB_REPO="${STEAKOS_HUB_REPO:-steakOS}"
ORG="${STEAKOS_GITHUB_ORG:-steakOS}"
REPO="${STEAKOS_GITHUB_REPO:-steakOS}"
RAW_BASE="${STEAKOS_RAW_BASE:-${HUB_HOST}/${HUB_OWNER}/${HUB_REPO}/raw/branch/main}"
BASE_URL="${STEAKOS_BASE_URL:-}"
PREFIX="${STEAKOS_PREFIX:-${HOME}/steakOS}"
OFFLINE_ZIP=""
NO_START=0
SKIP_DEPS=0
VERSION_PIN="${STEAKOS_VERSION:-}"
ASSUME_YES="${STEAKOS_ASSUME_YES:-0}"
BOOTSTRAP_DB=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

die() { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*"; }
warn() { echo "warn: $*" >&2; }

# Forgejo/Gitea has no GitHub-style …/releases/latest/download/ folder — resolve tag via API.
resolve_release_base() {
  if [[ -n "${STEAKOS_BASE_URL:-}" ]]; then
    echo "${STEAKOS_BASE_URL%/}"
    return
  fi
  need_cmd curl
  local meta tag
  meta="$(curl -fsSL "${HUB_HOST}/api/v1/repos/${HUB_OWNER}/${HUB_REPO}/releases/latest")" \
    || die "cannot fetch latest release metadata from ${HUB_HOST}"
  tag="$(echo "$meta" | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)"
  [[ -n "$tag" ]] || die "latest release has no tag_name"
  echo "${HUB_HOST}/${HUB_OWNER}/${HUB_REPO}/releases/download/${tag}"
}

usage() {
  sed -n '17,35p' "$0"
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url|-u) BASE_URL="${2:-}"; shift 2 ;;
    --prefix|-p)   PREFIX="${2:-}"; shift 2 ;;
    --offline|-f)  OFFLINE_ZIP="${2:-}"; shift 2 ;;
    --version|-v)  VERSION_PIN="${2:-}"; shift 2 ;;
    --no-start)    NO_START=1; shift ;;
    --skip-deps)   SKIP_DEPS=1; shift ;;
    --no-db)       BOOTSTRAP_DB=0; shift ;;
    -y|--yes)      ASSUME_YES=1; shift ;;
    -h|--help)     usage ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
done

confirm() {
  local prompt="$1"
  if [[ "$ASSUME_YES" == "1" ]]; then
    return 0
  fi
  if [[ ! -t 0 ]]; then
    die "refusing interactive prompt on non-TTY (pass --yes)"
  fi
  read -r -p "$prompt [y/N] " ans
  [[ "$ans" == "y" || "$ans" == "Y" || "$ans" == "yes" ]]
}

java_major() {
  local line ver
  line="$(java -version 2>&1 | head -1 || true)"
  # openjdk version "21.0.1"  or java version "1.8.0_392"
  ver="$(echo "$line" | sed -n 's/.*version "\([0-9][0-9.]*\)".*/\1/p')"
  if [[ -z "$ver" ]]; then
    echo 0
    return
  fi
  if [[ "$ver" == 1.* ]]; then
    echo "$ver" | cut -d. -f2
  else
    echo "$ver" | cut -d. -f1
  fi
}

need_java21() {
  if ! command -v java >/dev/null 2>&1; then
    return 0
  fi
  local maj
  maj="$(java_major)"
  [[ "${maj:-0}" -lt 21 ]]
}

detect_os() {
  case "$(uname -s)" in
    Linux*)
      if [[ -f /etc/os-release ]]; then
        # shellcheck disable=SC1091
        . /etc/os-release
        echo "${ID:-linux}"
      else
        echo linux
      fi
      ;;
    Darwin*) echo macos ;;
    *) echo unknown ;;
  esac
}

ensure_homebrew() {
  if command -v brew >/dev/null 2>&1; then
    return 0
  fi
  info "Homebrew is not installed"
  if confirm "Install Homebrew from https://brew.sh ?"; then
    need_cmd curl
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    # Apple Silicon path
    if [[ -x /opt/homebrew/bin/brew ]]; then
      eval "$(/opt/homebrew/bin/brew shellenv)"
    elif [[ -x /usr/local/bin/brew ]]; then
      eval "$(/usr/local/bin/brew shellenv)"
    fi
  else
    die "Homebrew required on macOS (see https://brew.sh) or install Temurin 21 + PostgreSQL 16 manually, then re-run with --skip-deps"
  fi
  command -v brew >/dev/null 2>&1 || die "brew still not on PATH after install"
}

install_java_linux() {
  info "Installing OpenJDK 21 (apt)"
  sudo apt-get update -y
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-21-jdk unzip curl ca-certificates
}

install_postgres_linux() {
  info "Installing PostgreSQL (apt)"
  sudo apt-get update -y
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y postgresql postgresql-contrib
  sudo systemctl enable --now postgresql || true
}

install_java_macos() {
  ensure_homebrew
  info "Installing Temurin 21 (Homebrew cask)"
  brew install --cask temurin@21 || brew install --cask temurin
}

install_postgres_macos() {
  ensure_homebrew
  info "Installing PostgreSQL 16 (Homebrew)"
  brew install postgresql@16 || brew install postgresql
  brew services start postgresql@16 2>/dev/null || brew services start postgresql || true
  # PATH for psql
  if [[ -d /opt/homebrew/opt/postgresql@16/bin ]]; then
    export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"
  elif [[ -d /usr/local/opt/postgresql@16/bin ]]; then
    export PATH="/usr/local/opt/postgresql@16/bin:$PATH"
  fi
}

postgres_ready() {
  if command -v pg_isready >/dev/null 2>&1; then
    pg_isready -h 127.0.0.1 -p 5432 >/dev/null 2>&1 && return 0
  fi
  # TCP probe
  if command -v bash >/dev/null 2>&1; then
    (echo >/dev/tcp/127.0.0.1/5432) >/dev/null 2>&1 && return 0
  fi
  return 1
}

run_db_setup() {
  local sql="$1"
  [[ -f "$sql" ]] || die "DB setup SQL not found: $sql"
  info "Creating steakOS databases (role + steakos_*)"
  local os
  os="$(detect_os)"
  case "$os" in
    ubuntu|linuxmint|debian|pop|elementary)
      sudo -u postgres psql -v ON_ERROR_STOP=1 -f "$sql"
      for db in steakos_dev steakos_test steakos_prod; do
        sudo -u postgres psql -d "$db" -v ON_ERROR_STOP=1 -c \
          "GRANT ALL ON SCHEMA public TO steakos; ALTER SCHEMA public OWNER TO steakos;" || true
      done
      ;;
    macos)
      # Homebrew postgres typically trusts local user as superuser
      if command -v psql >/dev/null 2>&1; then
        psql postgres -v ON_ERROR_STOP=1 -f "$sql" || psql -d postgres -v ON_ERROR_STOP=1 -f "$sql"
        for db in steakos_dev steakos_test steakos_prod; do
          psql -d "$db" -v ON_ERROR_STOP=1 -c \
            "GRANT ALL ON SCHEMA public TO steakos; ALTER SCHEMA public OWNER TO steakos;" || true
        done
      else
        warn "psql not found — skip DB bootstrap; run $sql manually"
      fi
      ;;
    *)
      if command -v sudo >/dev/null 2>&1 && id postgres >/dev/null 2>&1; then
        sudo -u postgres psql -v ON_ERROR_STOP=1 -f "$sql" || warn "DB setup failed"
      else
        warn "unknown OS for DB bootstrap — run $sql as a Postgres superuser"
      fi
      ;;
  esac
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

# --- deps ---------------------------------------------------------------------
OS="$(detect_os)"
info "OS: $OS ($(uname -m))"

if [[ "$SKIP_DEPS" -eq 0 ]]; then
  case "$OS" in
    ubuntu|linuxmint|debian|pop|elementary)
      if need_java21; then
        install_java_linux
      else
        info "Java $(java_major)+ already on PATH — skipping JDK install"
      fi
      if postgres_ready && command -v psql >/dev/null 2>&1; then
        info "PostgreSQL already responding on 5432 — skipping install"
      else
        install_postgres_linux
      fi
      ;;
    macos)
      if need_java21; then
        install_java_macos
      else
        info "Java $(java_major)+ already on PATH — skipping JDK install"
      fi
      if postgres_ready && command -v psql >/dev/null 2>&1; then
        info "PostgreSQL already responding on 5432 — skipping install"
      else
        install_postgres_macos
      fi
      ;;
    *)
      die "unsupported OS for bootstrap deps: $OS (use Ubuntu/Linux Mint, macOS, or --skip-deps after manual Java 21 + Postgres 16)"
      ;;
  esac
else
  info "Skipping dependency install (--skip-deps)"
fi

need_cmd java
maj="$(java_major)"
[[ "$maj" -ge 21 ]] || die "Java 21+ required (found major=$maj). Install Temurin/OpenJDK 21 and re-open this shell."

# --- fetch package installer if needed ----------------------------------------
TMP="$(mktemp -d)"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

INSTALLER="$SCRIPT_DIR/install.sh"
if [[ ! -f "$INSTALLER" ]]; then
  info "Fetching package installer from hub"
  need_cmd curl
  curl -fsSL "${RAW_BASE}/scripts/install/install.sh" -o "$TMP/install.sh"
  chmod +x "$TMP/install.sh"
  INSTALLER="$TMP/install.sh"
fi

# --- package install (may unpack SQL) -----------------------------------------
if [[ -z "$OFFLINE_ZIP" ]]; then
  if [[ -z "$BASE_URL" ]]; then
    BASE_URL="$(resolve_release_base)"
  fi
  BASE_URL="${BASE_URL%/}"
  info "Release package host: $BASE_URL"
fi

PKG_ARGS=(--prefix "$PREFIX" --base-url "$BASE_URL")
[[ -n "$OFFLINE_ZIP" ]] && PKG_ARGS=(--prefix "$PREFIX" --offline "$OFFLINE_ZIP")
[[ -n "$VERSION_PIN" ]] && PKG_ARGS+=(--version "$VERSION_PIN")
[[ "$NO_START" -eq 1 ]] && PKG_ARGS+=(--no-start)
[[ "$ASSUME_YES" == "1" ]] && PKG_ARGS+=(--yes)
# Require checksum on release hosts / GitHub / explicit flag (P5)
if [[ "$BASE_URL" == *github.com* || "$BASE_URL" == */releases/download/* || "${STEAKOS_REQUIRE_CHECKSUM:-0}" == "1" ]]; then
  export STEAKOS_REQUIRE_CHECKSUM=1
fi

info "Installing steakOS package"
# Install without start first so we can run DB SQL from the package tree
bash "$INSTALLER" "${PKG_ARGS[@]}" --no-start

SQL="$PREFIX/db/setup-postgresql.sql"
if [[ "$BOOTSTRAP_DB" -eq 1 ]]; then
  if [[ -f "$SQL" ]]; then
    run_db_setup "$SQL" || warn "DB bootstrap had errors — check Postgres and re-run SQL"
  else
    warn "No $SQL after package install"
  fi
fi

if [[ "$NO_START" -eq 0 ]]; then
  info "Starting steakOS"
  "$PREFIX/bin/steakos" start || warn "start failed — see $PREFIX/logs/steakos.log"
fi

echo ""
echo "steakOS bootstrap finished"
echo "  prefix : $PREFIX"
echo "  control: $PREFIX/bin/steakos {start|stop|status|logs|version}"
echo "  config : $PREFIX/config/steakos.env"
echo ""
echo "Open the URL printed by start (often http://127.0.0.1:9090)."
echo "Sign in, then change the default admin password before sharing the machine."
echo "Prefer to read the script first next time? Save bootstrap.sh from GitHub and run it locally."
