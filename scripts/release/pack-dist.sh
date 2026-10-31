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

# Build a runnable steakOS distribution (bootJar + SPA assets + helpers + checksums).
#
# Usage (from anywhere):
#   ./scripts/release/pack-dist.sh
#   ./scripts/release/pack-dist.sh --skip-frontend   # reuse existing frontend/dist
#   ./scripts/release/pack-dist.sh --skip-jar        # reuse existing build/libs/*.jar
#   STEAKOS_BUILD_ID=20260728T1200 ./scripts/release/pack-dist.sh
#
# Output:
#   dist/out/steakOS-V<semver>-<buildId>/     # unpacked layout
#   dist/out/steakOS-V<semver>-<buildId>.zip
#   dist/out/checksums.sha256
#   dist/out/install.sh  install.ps1          # copies for static hosting (LAN)
#   dist/out/latest -> steakOS-V…/            # symlink to newest pack (best-effort)
#
# Layout inside the package (install scripts expect this):
#   app/steakOS-<semver>.jar
#   web/                     # Vite production build (serve via reverse proxy or future embed)
#   bin/steakos  bin/steakos.ps1
#   db/setup-postgresql.sql
#   config/steakos.env.example
#   VERSION  CHECKSUMS  README.txt
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

SKIP_FRONTEND=0
SKIP_JAR=0
for arg in "$@"; do
  case "$arg" in
    --skip-frontend) SKIP_FRONTEND=1 ;;
    --skip-jar) SKIP_JAR=1 ;;
    -h|--help)
      sed -n '2,30p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 2
      ;;
  esac
done

# --- version / stamp ----------------------------------------------------------
GRADLE_V="$(grep -E '^\s*version\s*=' build.gradle | head -1 | sed -E 's/.*"([^"]+)".*/\1/' || true)"
SEMVER="${GRADLE_V:-0.1.0}"
# strip any -SNAPSHOT etc for folder name
SEMVER_CLEAN="${SEMVER%%-*}"
LINE="${STEAKOS_PRODUCT_LINE:-SBM}"
R_MAJOR="${STEAKOS_RELEASE_MAJOR:-1}"
R_MINOR="${STEAKOS_RELEASE_MINOR:-0}"
BUILD_ID="${STEAKOS_BUILD_ID:-$(date -u +%Y%m%dT%H%M)}"
V_LABEL="V${SEMVER_CLEAN}"
PKG_NAME="steakOS-${V_LABEL}-${BUILD_ID}"
PRODUCT_STAMP="${LINE}-R${R_MAJOR}.${R_MINOR}-${V_LABEL}-${BUILD_ID}"

OUT_ROOT="${STEAKOS_DIST_OUT:-$ROOT/dist/out}"
STAGE="$OUT_ROOT/$PKG_NAME"
ZIP_PATH="$OUT_ROOT/${PKG_NAME}.zip"

echo "==> steakOS pack-dist"
echo "    semver     : $SEMVER_CLEAN"
echo "    build id   : $BUILD_ID"
echo "    product    : $PRODUCT_STAMP"
echo "    stage      : $STAGE"

rm -rf "$STAGE"
mkdir -p "$STAGE"/{app,web,bin,db,config}

# --- frontend -----------------------------------------------------------------
if [[ "$SKIP_FRONTEND" -eq 0 ]]; then
  echo "==> Building frontend (npm run build)"
  if [[ ! -d frontend/node_modules ]]; then
    (cd frontend && npm ci)
  else
    (cd frontend && npm install --no-audit --no-fund)
  fi
  (cd frontend && npm run build)
else
  echo "==> Skipping frontend build (--skip-frontend)"
  if [[ ! -f frontend/dist/index.html ]]; then
    echo "error: frontend/dist missing; run without --skip-frontend" >&2
    exit 1
  fi
fi

echo "==> Copying SPA → web/"
cp -a frontend/dist/. "$STAGE/web/"

# --- bootJar ------------------------------------------------------------------
if [[ "$SKIP_JAR" -eq 0 ]]; then
  echo "==> Building bootJar"
  ./gradlew bootJar -x test -x integrationTest
else
  echo "==> Skipping bootJar (--skip-jar)"
fi

JAR_SRC="$(ls -1t build/libs/steakOS-*.jar 2>/dev/null | head -1 || true)"
if [[ -z "$JAR_SRC" || ! -f "$JAR_SRC" ]]; then
  echo "error: no build/libs/steakOS-*.jar found" >&2
  exit 1
fi
JAR_BASE="$(basename "$JAR_SRC")"
cp -a "$JAR_SRC" "$STAGE/app/$JAR_BASE"
# stable name for start scripts
cp -a "$JAR_SRC" "$STAGE/app/steakOS.jar"
echo "    jar        : $JAR_BASE"

# --- db + config + helpers ----------------------------------------------------
cp -a db/setup-postgresql.sql "$STAGE/db/"

cat > "$STAGE/config/steakos.env.example" <<EOF
# steakOS runtime environment (copy to steakos.env and edit)
# Loaded by bin/steakos and bin/steakos.ps1

# Grails / Spring
GRAILS_ENV=production
# Quote multi-word values — this file is shell-sourced on Linux/macOS
JAVA_OPTS="-Xms256m -Xmx640m -XX:+ExitOnOutOfMemoryError"

# HTTP (production default 9090 — see application.yml)
SERVER_PORT=9090
SERVER_ADDRESS=0.0.0.0

# PostgreSQL
STEAKOS_DB_HOST=localhost
STEAKOS_DB_PORT=5432
STEAKOS_DB_USER=steakos
STEAKOS_DB_PASSWORD=steakos
# production datasource uses steakos_prod; override URL only if needed:
# SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/steakos_prod

# Product stamp (optional; empty build id → generated at process start)
STEAKOS_PRODUCT_LINE=${LINE}
STEAKOS_RELEASE_MAJOR=${R_MAJOR}
STEAKOS_RELEASE_MINOR=${R_MINOR}
STEAKOS_BUILD_ID=${BUILD_ID}

# Where the browser UI is expected (SPA). For dist packages, either reverse-proxy
# web/ onto this origin or run Vite in development.
STEAKOS_FRONTEND_URL=http://localhost:9090
EOF

# Start helpers live in scripts/install/runtime/ and are copied into the package
install -m 0755 "$ROOT/scripts/install/runtime/steakos" "$STAGE/bin/steakos"
install -m 0644 "$ROOT/scripts/install/runtime/steakos.ps1" "$STAGE/bin/steakos.ps1"

# --- VERSION / README ---------------------------------------------------------
cat > "$STAGE/VERSION" <<EOF
productLine=${LINE}
release=R${R_MAJOR}.${R_MINOR}
softwareVersion=${SEMVER_CLEAN}
V=${V_LABEL}
buildId=${BUILD_ID}
productStamp=${PRODUCT_STAMP}
jar=${JAR_BASE}
packedAtUtc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF

cat > "$STAGE/README.txt" <<EOF
steakOS distribution
====================
Stamp:  ${PRODUCT_STAMP}
Jar:    app/${JAR_BASE}
SPA:    web/   (static Vite build — use a reverse proxy, or development Vite)

Quick start
-----------
1. PostgreSQL: create DBs with db/setup-postgresql.sql (see docs/installation/QUICKSTART.md)
2. cp config/steakos.env.example config/steakos.env   # edit passwords
3. bin/steakos start          # Linux/macOS
   or:  powershell -File bin/steakos.ps1 start
4. Open http://localhost:9090  (API / Grails). Full React UI is in web/;
   for day-to-day eval prefer the installer's default path in QUICKSTART.md.

Commands:  start | stop | status | logs | version
EOF

# Package-level checksum of contents (paths relative to package root)
echo "==> Writing package CHECKSUMS"
(
  cd "$STAGE"
  # portable: sha256sum on Linux, shasum on macOS
  if command -v sha256sum >/dev/null 2>&1; then
    find . -type f ! -name CHECKSUMS -print0 | sort -z | xargs -0 sha256sum
  else
    find . -type f ! -name CHECKSUMS -print0 | sort -z | xargs -0 shasum -a 256
  fi
) > "$STAGE/CHECKSUMS"

# --- zip ----------------------------------------------------------------------
echo "==> Creating zip"
mkdir -p "$OUT_ROOT"
rm -f "$ZIP_PATH"
(
  cd "$OUT_ROOT"
  if command -v zip >/dev/null 2>&1; then
    zip -qr "${PKG_NAME}.zip" "$PKG_NAME"
  else
    # fallback without zip(1)
    tar -czf "${PKG_NAME}.tar.gz" "$PKG_NAME"
    ZIP_PATH="$OUT_ROOT/${PKG_NAME}.tar.gz"
    echo "    note: zip not found; wrote ${PKG_NAME}.tar.gz"
  fi
)

# Host-level checksums (artifacts only)
echo "==> Writing dist/out/checksums.sha256"
(
  cd "$OUT_ROOT"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$(basename "$ZIP_PATH")" "$PKG_NAME/VERSION" 2>/dev/null || sha256sum "$(basename "$ZIP_PATH")"
  else
    shasum -a 256 "$(basename "$ZIP_PATH")"
  fi
) | tee "$OUT_ROOT/checksums.sha256"

# Publish install entrypoints next to artifacts (for static HTTP hosting)
cp -a "$ROOT/scripts/install/install.sh" "$OUT_ROOT/install.sh"
cp -a "$ROOT/scripts/install/install.ps1" "$OUT_ROOT/install.ps1"
if [[ -f "$ROOT/scripts/install/bootstrap.sh" ]]; then
  cp -a "$ROOT/scripts/install/bootstrap.sh" "$OUT_ROOT/bootstrap.sh"
  chmod +x "$OUT_ROOT/bootstrap.sh"
fi
if [[ -f "$ROOT/scripts/install/bootstrap.ps1" ]]; then
  cp -a "$ROOT/scripts/install/bootstrap.ps1" "$OUT_ROOT/bootstrap.ps1"
fi
chmod +x "$OUT_ROOT/install.sh"

# latest pointer (symlink when possible)
rm -f "$OUT_ROOT/latest" "$OUT_ROOT/latest.zip" 2>/dev/null || true
ln -sfn "$PKG_NAME" "$OUT_ROOT/latest" 2>/dev/null || true
ln -sfn "$(basename "$ZIP_PATH")" "$OUT_ROOT/latest.zip" 2>/dev/null || true

# machine-readable pointer for installers
cat > "$OUT_ROOT/latest.json" <<EOF
{
  "package": "${PKG_NAME}",
  "zip": "$(basename "$ZIP_PATH")",
  "softwareVersion": "${SEMVER_CLEAN}",
  "V": "${V_LABEL}",
  "buildId": "${BUILD_ID}",
  "productStamp": "${PRODUCT_STAMP}"
}
EOF

echo ""
echo "OK  package : $STAGE"
echo "OK  archive : $ZIP_PATH"
echo "OK  sums    : $OUT_ROOT/checksums.sha256"
echo "OK  host    : $OUT_ROOT/install.sh  install.ps1  latest.json"
echo ""
echo "LAN host example:"
echo "  cd dist/out && python3 -m http.server 8088 --bind 0.0.0.0"
echo "  # clients: curl -fsSL http://<this-host>:8088/install.sh | bash -s -- --base-url http://<this-host>:8088"
