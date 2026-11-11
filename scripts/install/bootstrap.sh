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
#   curl -fsSL https://raw.githubusercontent.com/steakOS/steakOS/main/scripts/install/bootstrap.sh | bash
#   ./bootstrap.sh --yes
#   ./bootstrap.sh --skip-deps --base-url https://github.com/steakOS/steakOS/releases/latest/download
#   ./bootstrap.sh --offline /path/to/steakOS-….zip
#
# Environment:
#   STEAKOS_CHANNEL      github (default) | forgejo — install host family
#   STEAKOS_GITHUB_ORG / STEAKOS_GITHUB_REPO  (default steakOS / steakOS)
#   STEAKOS_BASE_URL     package host (default: GitHub releases/latest/download)
#   STEAKOS_RAW_BASE     raw file base for fetching install.sh when not local
#   STEAKOS_HUB_HOST / STEAKOS_HUB_OWNER / STEAKOS_HUB_REPO  (Forgejo override when CHANNEL=forgejo)
#   STEAKOS_PREFIX       install dir (default: $HOME/steakOS)
#   STEAKOS_ASSUME_YES   1 = non-interactive yes
set -euo pipefail

# Default install channel: public GitHub repo https://github.com/steakOS/steakOS
# Office Forgejo: STEAKOS_CHANNEL=forgejo (optional LAN override).
ORG="${STEAKOS_GITHUB_ORG:-steakOS}"
REPO="${STEAKOS_GITHUB_REPO:-steakOS}"
CHANNEL="${STEAKOS_CHANNEL:-github}"
HUB_HOST="${STEAKOS_HUB_HOST:-http://umbrel.local:8101}"
HUB_OWNER="${STEAKOS_HUB_OWNER:-hermesbro}"
HUB_REPO="${STEAKOS_HUB_REPO:-steakOS}"
if [[ -n "${STEAKOS_RAW_BASE:-}" ]]; then
  RAW_BASE="${STEAKOS_RAW_BASE}"
elif [[ "$CHANNEL" == "forgejo" ]]; then
  RAW_BASE="${HUB_HOST}/${HUB_OWNER}/${HUB_REPO}/raw/branch/main"
else
  RAW_BASE="https://raw.githubusercontent.com/${ORG}/${REPO}/main"
fi
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

# GitHub: …/releases/latest/download works as a CDN redirect for release assets.
# Forgejo/Gitea: resolve tag via API, then …/releases/download/<tag>.
resolve_release_base() {
  if [[ -n "${STEAKOS_BASE_URL:-}" ]]; then
    echo "${STEAKOS_BASE_URL%/}"
    return
  fi
  if [[ "$CHANNEL" != "forgejo" ]]; then
    echo "https://github.com/${ORG}/${REPO}/releases/latest/download"
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
  info "Fetching package installer from ${RAW_BASE}"
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
# Install without start first so we can run DB SQL from the package tree.
# set -e stops here if the package download or extract fails.
bash "$INSTALLER" "${PKG_ARGS[@]}" --no-start

SQL="$PREFIX/db/setup-postgresql.sql"
if [[ "$BOOTSTRAP_DB" -eq 1 ]]; then
  if [[ -f "$SQL" ]]; then
    run_db_setup "$SQL" || warn "DB bootstrap had errors — check Postgres and re-run SQL"
  else
    warn "No $SQL after package install"
  fi
fi

# PDR-036 / ADR-058: do not start until the schema pack is applied to steakos_prod.
SCHEMA="$(ls -1 "$PREFIX"/db/schema/steakOS-*.sql 2>/dev/null | sort | tail -1 || true)"
if [[ -z "$SCHEMA" || ! -f "$SCHEMA" ]]; then
  die "Production database has no steakOS schema pack in this package. Re-run the installer when the release includes db/schema. Do not switch to development."
fi
if command -v psql >/dev/null 2>&1 || [[ -x /usr/bin/psql ]]; then
  :
else
  die "psql not found. Cannot apply the production schema pack. Do not switch to development."
fi
marked="$(psql -U postgres -d steakos_prod -tAc "SELECT to_regclass('public.schema_pack')" 2>/dev/null || sudo -u postgres psql -d steakos_prod -tAc "SELECT to_regclass('public.schema_pack')" 2>/dev/null || true)"
psql_prod() {
  if command -v sudo >/dev/null 2>&1 && id postgres >/dev/null 2>&1; then
    sudo -u postgres psql -d steakos_prod "$@"
  else
    psql -U postgres -d steakos_prod "$@"
  fi
}
if [[ "$marked" == *schema_pack* ]]; then
  info "schema_pack already present — not reapplying schema"
else
  info "Applying production schema $(basename "$SCHEMA")"
  psql_prod -v ON_ERROR_STOP=1 -f "$SCHEMA" || die "Schema pack failed. Do not switch to development."
fi
# Applied as postgres; the app connects as steakos.
info "Granting steakos access to steakos_prod tables"
psql_prod -v ON_ERROR_STOP=1 <<'SQL' || die "Could not grant steakos access to steakos_prod. Do not switch to development."
GRANT USAGE, CREATE ON SCHEMA public TO steakos;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO steakos;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO steakos;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON TABLES TO steakos;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON SEQUENCES TO steakos;
SQL

ENV_FILE="$PREFIX/config/steakos.env"
if [[ -f "$ENV_FILE" ]]; then
  if grep -qE '^[[:space:]]*GRAILS_ENV=' "$ENV_FILE"; then
    sed -i.bak -E 's/^[[:space:]]*GRAILS_ENV=.*/GRAILS_ENV=production/' "$ENV_FILE"
    rm -f "$ENV_FILE.bak"
  else
    printf '\nGRAILS_ENV=production\n' >> "$ENV_FILE"
  fi
fi
export GRAILS_ENV=production

PORT="9090"
if [[ -f "$ENV_FILE" ]]; then
  line="$(grep -E '^[[:space:]]*SERVER_PORT=' "$ENV_FILE" | tail -1 || true)"
  if [[ "$line" =~ =([0-9]+) ]]; then
    PORT="${BASH_REMATCH[1]}"
  fi
fi

STARTED=0
if [[ "$NO_START" -eq 0 ]]; then
  info "Starting steakOS (production)"
  if "$PREFIX/bin/steakos" start; then
    STARTED=1
  else
    warn "start failed — see $PREFIX/logs/steakos.log"
  fi
fi

OPEN_URL=""
if [[ "$STARTED" -eq 1 ]]; then
  info "Waiting until Grails answers on port ${PORT} (up to 4 minutes)"
  deadline=$((SECONDS + 240))
  ready=0
  while (( SECONDS < deadline )); do
    if [[ -f "$PREFIX/logs/steakos.err.log" ]] && grep -q 'Application run failed' "$PREFIX/logs/steakos.err.log"; then
      warn "Grails exited during startup. See $PREFIX/logs/steakos.err.log"
      STARTED=0
      break
    fi
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:${PORT}/login/auth" || true)"
    if [[ "$code" =~ ^[23] ]]; then
      ready=1
      break
    fi
    sleep 3
  done
  if [[ "$ready" -eq 1 ]]; then
    tour_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:${PORT}/tour/index.html" || true)"
    if [[ "$tour_code" == "200" ]]; then
      OPEN_URL="http://127.0.0.1:${PORT}/tour/index.html"
    else
      OPEN_URL="http://127.0.0.1:${PORT}/login/auth"
      info "Tour page is not served by this build. Opening the login page instead."
    fi
    info "Opening $OPEN_URL"
    if command -v xdg-open >/dev/null 2>&1; then xdg-open "$OPEN_URL" >/dev/null 2>&1 || true
    elif command -v open >/dev/null 2>&1; then open "$OPEN_URL" >/dev/null 2>&1 || true
    fi
  else
    warn "Grails did not answer on port ${PORT} within 4 minutes."
  fi
fi

echo ""
if [[ "$STARTED" -eq 1 && -n "$OPEN_URL" ]]; then
  echo "steakOS is up."
  echo "Opened $OPEN_URL"
  echo ""
  echo "  http://127.0.0.1:${PORT}/login/auth"
  echo "  http://127.0.0.1:${PORT}/tour/index.html"
  echo ""
  echo "Use 127.0.0.1 if localhost refuses. Admin and CEO passwords are set in the wizard, not in this download."
elif [[ "$NO_START" -eq 1 ]]; then
  echo "Package installed under $PREFIX (not started; --no-start)."
  echo "When ready: GRAILS_ENV=production $PREFIX/bin/steakos start"
  echo "Then open http://127.0.0.1:${PORT}/tour/ and http://localhost:${PORT}/tour/"
else
  echo "Install finished but steakOS did not start."
  echo "Fix the error in $PREFIX/logs/steakos.log, then: GRAILS_ENV=production $PREFIX/bin/steakos start"
fi