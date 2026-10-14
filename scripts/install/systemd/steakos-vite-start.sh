#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The steakOS Authors
#
# Reliable ExecStart for steakos-vite.service (OI-V06).
# Ensures frontend deps exist, then runs the Vite dev server (:9001).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FE="$ROOT/frontend"
cd "$FE"

command -v npm >/dev/null 2>&1 || {
  echo "steakos-vite-start: npm not found" >&2
  exit 1
}

if [[ ! -d node_modules ]]; then
  echo "steakos-vite-start: installing frontend dependencies…"
  npm install --no-fund --no-audit
fi

exec npm run dev
