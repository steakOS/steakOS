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

# Print / apply Docker image tag from product version rules (VERSIONS.md V7).
# Usage:
#   ./scripts/docker-tag.sh              # print tag only
#   ./scripts/docker-tag.sh --build      # docker build -t steakos:<tag> .
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

LINE="${STEAKOS_PRODUCT_LINE:-SBM}"
R_MAJOR="${STEAKOS_RELEASE_MAJOR:-1}"
R_MINOR="${STEAKOS_RELEASE_MINOR:-0}"
# Prefer Gradle version; fall back to 0.1.6
GRADLE_V="$(grep -E '^\s*version\s*=' build.gradle | head -1 | sed -E 's/.*"([^"]+)".*/\1/' || true)"
SEMVER="${GRADLE_V:-0.1.6}"
# Normalize to major.minor.patch
IFS='.' read -r MA MI PA <<<"${SEMVER//-*/}"
MA="${MA:-0}"; MI="${MI:-0}"; PA="${PA:-0}"
V="V${MA}.${MI}.${PA}"
BUILD_ID="${STEAKOS_BUILD_ID:-$(date -u +%Y%m%dT%H%M)}"

# Docker tags cannot contain '+'
TAG="${LINE}-R${R_MAJOR}.${R_MINOR}-${V}-${BUILD_ID}"

echo "$TAG"

if [[ "${1:-}" == "--build" ]]; then
  JAR="$(ls -1 build/libs/steakOS-*.jar 2>/dev/null | grep -v -- '-plain' | sort | tail -1 || true)"
  if [[ -z "$JAR" ]]; then
    echo "boot jar missing. Run ./gradlew bootJar first." >&2
    exit 1
  fi
  docker build \
    -f deploy/Dockerfile \
    --build-arg "JAR=${JAR}" \
    --build-arg "STEAKOS_PRODUCT_LINE=${LINE}" \
    --build-arg "STEAKOS_RELEASE_MAJOR=${R_MAJOR}" \
    --build-arg "STEAKOS_RELEASE_MINOR=${R_MINOR}" \
    --build-arg "STEAKOS_BUILD_ID=${BUILD_ID}" \
    --build-arg "STEAKOS_VERSION=${MA}.${MI}.${PA}" \
    -t "steakos:${TAG}" \
    .
  echo "Built steakos:${TAG}"
fi
