#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The steakOS Authors
#
# Reliable ExecStart for steakos-api.service (OI-V06 / ADR-036).
# Resolves the newest build/libs/steakOS-*.jar unless STEAKOS_JAR is set.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$ROOT"

if [[ -n "${STEAKOS_JAR:-}" ]]; then
  JAR="$STEAKOS_JAR"
elif [[ -f "$ROOT/build/libs/steakOS.jar" ]]; then
  JAR="$ROOT/build/libs/steakOS.jar"
else
  # Prefer highest SemVer filename; fail clearly if none.
  mapfile -t CANDIDATES < <(ls -1 "$ROOT"/build/libs/steakOS-*.jar 2>/dev/null | sort -V || true)
  if [[ ${#CANDIDATES[@]} -eq 0 ]]; then
    echo "steakos-api-start: no bootJar under $ROOT/build/libs/" >&2
    echo "Build once: cd \"$ROOT\" && ./gradlew bootJar" >&2
    exit 1
  fi
  JAR="${CANDIDATES[-1]}"
fi

[[ -f "$JAR" ]] || { echo "steakos-api-start: missing jar: $JAR" >&2; exit 1; }

JAVA_BIN="${STEAKOS_JAVA:-}"
if [[ -z "$JAVA_BIN" ]]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
  else
    echo "steakos-api-start: java not found (need JRE 17+)" >&2
    exit 1
  fi
fi

GRAILS_ENV="${GRAILS_ENV:-development}"
# ADR-036: fat-jar long sessions — reload off + heap cap + OOM exit
JAVA_OPTS="${JAVA_OPTS:--Dgrails.env=${GRAILS_ENV} -Dgrails.reload.enabled=false -Xmx640m -XX:+ExitOnOutOfMemoryError}"

echo "steakos-api-start: jar=$JAR env=$GRAILS_ENV"
# shellcheck disable=SC2086
exec "$JAVA_BIN" $JAVA_OPTS -jar "$JAR"
