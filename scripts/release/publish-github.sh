#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The steakOS Authors
#
# Publish a Forgejo Release cut to public GitHub (ADR-057).
#
# Forgejo is the write hub. Creating a Forgejo Release for tag vX.Y.Z is the
# gate; this script mirrors that tag + Release assets to GitHub steakOS/steakOS.
# Ordinary git push to Forgejo does NOT invoke this path.
#
# Usage (from hub root or anywhere):
#   ./scripts/release/publish-github.sh --tag v0.2.4
#   ./scripts/release/publish-github.sh --tag v0.2.4 --dry-run
#   ./scripts/release/publish-github.sh --latest
#   ./scripts/release/publish-github.sh --tag v0.2.4 --push-main    # also update GitHub main
#   ./scripts/release/publish-github.sh --tag v0.2.4 --force-main   # first sync (replace Initial commit)
#   ./scripts/release/publish-github.sh --tag v0.2.4 --local-dist   # prefer dist/out over Forgejo download
#   ./scripts/release/publish-github.sh --tag v0.2.4 --skip-git     # assets only (tag already on GitHub)
#   ./scripts/release/publish-github.sh --tag v0.2.4 --skip-assets  # git/tag/release metadata only
#
# Prerequisites:
#   - Forgejo Release already exists for the tag (pack-dist + upload done)
#   - source ~/.config/forgejo/load.sh  (FORGEJO_USER / FORGEJO_PASSWORD)
#   - GitHub auth: GITHUB_TOKEN / GH_TOKEN (PAT — not account password),
#     or source ~/.config/github/load.sh, or `gh auth login && gh auth setup-git`
#   - Git never accepts your GitHub account password over HTTPS; only a PAT/token.
#
# Env overrides:
#   FORGEJO_BASE_URL  FORGEJO_OWNER  FORGEJO_REPO   (defaults: umbrel hub)
#   GITHUB_OWNER      GITHUB_REPO                   (defaults: steakOS/steakOS)
#   STEAKOS_DIST_OUT                                (local dist/out for --local-dist)
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

TAG=""
USE_LATEST=0
DRY_RUN=0
SKIP_GIT=0
SKIP_ASSETS=0
LOCAL_DIST=0
ALLOW_PRERELEASE=0
PUSH_MAIN=0
FORCE_MAIN=0

usage() {
  sed -n '2,45p' "$0"
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      TAG="${2:-}"
      shift 2
      ;;
    --latest)
      USE_LATEST=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --skip-git)
      SKIP_GIT=1
      shift
      ;;
    --skip-assets)
      SKIP_ASSETS=1
      shift
      ;;
    --local-dist)
      LOCAL_DIST=1
      shift
      ;;
    --allow-prerelease)
      ALLOW_PRERELEASE=1
      shift
      ;;
    --push-main)
      PUSH_MAIN=1
      shift
      ;;
    --force-main)
      # One-time: allow non-ff update of github main (unrelated Initial commit)
      FORCE_MAIN=1
      PUSH_MAIN=1
      shift
      ;;
    -h|--help)
      usage 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage 2
      ;;
  esac
done

if [[ "$USE_LATEST" -eq 0 && -z "$TAG" ]]; then
  echo "error: pass --tag vX.Y.Z or --latest" >&2
  usage 2
fi

# --- load secrets (never echo values) -----------------------------------------
if [[ -f "${HOME}/.config/forgejo/load.sh" ]]; then
  # shellcheck source=/dev/null
  source "${HOME}/.config/forgejo/load.sh"
fi
if [[ -f "${HOME}/.config/github/load.sh" ]]; then
  # shellcheck source=/dev/null
  source "${HOME}/.config/github/load.sh"
fi

FORGEJO_BASE_URL="${FORGEJO_BASE_URL:-http://umbrel.local:8101}"
FORGEJO_OWNER="${FORGEJO_OWNER:-hermesbro}"
FORGEJO_REPO="${FORGEJO_REPO:-steakOS}"
GITHUB_OWNER="${GITHUB_OWNER:-steakOS}"
GITHUB_REPO="${GITHUB_REPO:-steakOS}"
GITHUB_API="${GITHUB_API:-https://api.github.com}"
DIST_OUT="${STEAKOS_DIST_OUT:-$ROOT/dist/out}"

GITHUB_TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-}}"

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "error: required command not found: $1" >&2
    exit 1
  }
}

need_cmd curl
need_cmd python3
need_cmd git
need_cmd jq

if [[ -z "${FORGEJO_USER:-}" || -z "${FORGEJO_PASSWORD:-}" ]]; then
  echo "error: Forgejo credentials missing — source ~/.config/forgejo/load.sh" >&2
  exit 1
fi

if [[ -z "$GITHUB_TOKEN" ]] && ! gh auth status >/dev/null 2>&1; then
  echo "error: GitHub auth missing — set GITHUB_TOKEN / GH_TOKEN, source ~/.config/github/load.sh, or gh auth login" >&2
  exit 1
fi

forgejo_auth=(-u "${FORGEJO_USER}:${FORGEJO_PASSWORD}")
fj_api() {
  local path="$1"
  shift
  curl -sS "${forgejo_auth[@]}" "${FORGEJO_BASE_URL}/api/v1${path}" "$@"
}

gh_auth_args=()
if [[ -n "$GITHUB_TOKEN" ]]; then
  gh_auth_args=(-H "Authorization: Bearer ${GITHUB_TOKEN}" -H "X-GitHub-Api-Version: 2022-11-28")
fi

gh_api() {
  local method="$1"
  local path="$2"
  shift 2
  curl -sS "${gh_auth_args[@]}" -H "Accept: application/vnd.github+json" \
    -X "$method" "${GITHUB_API}${path}" "$@"
}

run() {
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "DRY-RUN:" "$@"
  else
    "$@"
  fi
}

echo "==> publish-github (ADR-057)"
echo "    forgejo    : ${FORGEJO_BASE_URL}/${FORGEJO_OWNER}/${FORGEJO_REPO}"
echo "    github     : ${GITHUB_OWNER}/${GITHUB_REPO}"
echo "    dry-run    : ${DRY_RUN}"

# --- resolve Forgejo release --------------------------------------------------
if [[ "$USE_LATEST" -eq 1 ]]; then
  FJ_JSON=$(fj_api "/repos/${FORGEJO_OWNER}/${FORGEJO_REPO}/releases/latest")
  TAG=$(echo "$FJ_JSON" | jq -r '.tag_name // empty')
  if [[ -z "$TAG" || "$TAG" == "null" ]]; then
    echo "error: no Forgejo latest release" >&2
    echo "$FJ_JSON" | head -c 400 >&2
    exit 1
  fi
  echo "    latest tag : $TAG"
else
  FJ_JSON=$(fj_api "/repos/${FORGEJO_OWNER}/${FORGEJO_REPO}/releases/tags/${TAG}")
fi

echo "$FJ_JSON" | jq -e '.id' >/dev/null 2>&1 || {
  echo "error: Forgejo release not found for tag ${TAG}" >&2
  echo "$FJ_JSON" | head -c 400 >&2
  echo >&2
  exit 1
}

DRAFT=$(echo "$FJ_JSON" | jq -r '.draft // false')
PRERELEASE=$(echo "$FJ_JSON" | jq -r '.prerelease // false')
REL_NAME=$(echo "$FJ_JSON" | jq -r '.name // .tag_name')
REL_BODY=$(echo "$FJ_JSON" | jq -r '.body // ""')
TARGET=$(echo "$FJ_JSON" | jq -r '.target_commitish // "main"')
ASSET_COUNT=$(echo "$FJ_JSON" | jq '.assets | length')

echo "    release    : ${REL_NAME} (draft=${DRAFT} prerelease=${PRERELEASE} assets=${ASSET_COUNT})"

if [[ "$DRAFT" == "true" ]]; then
  echo "error: Forgejo release is draft — publish it first (or this is not the public gate)" >&2
  exit 1
fi
if [[ "$PRERELEASE" == "true" && "$ALLOW_PRERELEASE" -eq 0 ]]; then
  echo "error: Forgejo release is prerelease — pass --allow-prerelease to mirror anyway" >&2
  exit 1
fi

# --- git: ensure tag exists locally, push to github remote --------------------
# Never embed the token in the remote URL. Use GIT_ASKPASS (x-access-token + PAT)
# or gh's credential helper — same pattern as Forgejo pushes.
ensure_github_remote() {
  if git remote get-url github >/dev/null 2>&1; then
    return 0
  fi
  local url="https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git"
  echo "    adding remote github → ${url}"
  run git remote add github "$url"
}

with_github_git_auth() {
  # Run git with HTTPS auth. Args: git subcommand + args (e.g. push github tag)
  local askpass ec
  askpass=$(mktemp)
  chmod 700 "$askpass"
  cat >"$askpass" <<'EOS'
#!/usr/bin/env bash
case "$1" in
  *Username*) printf '%s\n' "${GIT_USER:-x-access-token}" ;;
  *Password*) printf '%s\n' "${GIT_PASS}" ;;
  *) printf '\n' ;;
esac
EOS
  if [[ -n "${GITHUB_TOKEN:-}" ]]; then
    GIT_ASKPASS="$askpass" GIT_TERMINAL_PROMPT=0 \
      GIT_USER=x-access-token GIT_PASS="$GITHUB_TOKEN" \
      git -c credential.helper= "$@"
    ec=$?
  elif command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    gh auth setup-git >/dev/null 2>&1 || true
    GIT_TERMINAL_PROMPT=0 git "$@"
    ec=$?
  else
    rm -f "$askpass"
    echo "error: no GITHUB_TOKEN and gh is not logged in" >&2
    return 1
  fi
  rm -f "$askpass"
  return "$ec"
}

if [[ "$SKIP_GIT" -eq 0 ]]; then
  ensure_github_remote
  if ! git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
    echo "    fetching tag ${TAG} from origin (Forgejo)"
    run git fetch origin "refs/tags/${TAG}:refs/tags/${TAG}"
  fi

  echo "    preflight  : git ls-remote"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "DRY-RUN: git ls-remote https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git HEAD"
  else
    if ! with_github_git_auth ls-remote "https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git" HEAD >/dev/null; then
      echo "error: GitHub Git auth failed (ls-remote)." >&2
      echo "  Use a PAT (Contents: Read and write), not your GitHub account password." >&2
      echo "  source ~/.config/github/load.sh   # or: gh auth login && gh auth setup-git" >&2
      exit 1
    fi
    echo "    preflight  : ok"
  fi

  echo "    pushing tag ${TAG} → github"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "DRY-RUN: git push github refs/tags/${TAG}"
  else
    with_github_git_auth push github "refs/tags/${TAG}"
  fi
  if [[ "$PUSH_MAIN" -eq 1 ]]; then
    echo "    pushing ${TARGET}:main → github (force=${FORCE_MAIN})"
    if [[ "$DRY_RUN" -eq 1 ]]; then
      if [[ "$FORCE_MAIN" -eq 1 ]]; then
        echo "DRY-RUN: git push --force github ${TARGET}:main"
      else
        echo "DRY-RUN: git push github ${TARGET}:main"
      fi
    else
      if [[ "$FORCE_MAIN" -eq 1 ]]; then
        with_github_git_auth push --force github "${TARGET}:main"
      elif ! with_github_git_auth push github "${TARGET}:main"; then
        echo "error: push to github main failed (often unrelated histories)." >&2
        echo "  One-time align: re-run with --force-main" >&2
        echo "  (replaces GitHub main with the Forgejo release tip — ADR-057)" >&2
        exit 1
      fi
    fi
  fi
else
  echo "    skip-git   : yes"
fi

# --- GitHub Release create/update ---------------------------------------------
EXISTING_GH=$(gh_api GET "/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/tags/${TAG}" || true)
GH_REL_ID=$(echo "$EXISTING_GH" | jq -r '.id // empty' 2>/dev/null || true)

BODY_FILE=$(mktemp)
printf '%s\n' "$REL_BODY" >"$BODY_FILE"

if [[ -z "$GH_REL_ID" ]]; then
  echo "    creating GitHub Release ${REL_NAME}"
  PAYLOAD=$(jq -n \
    --arg tag "$TAG" \
    --arg name "$REL_NAME" \
    --arg body "$(cat "$BODY_FILE")" \
    --arg target "$TARGET" \
    --argjson prerelease "$PRERELEASE" \
    '{tag_name:$tag, name:$name, body:$body, target_commitish:$target, draft:false, prerelease:$prerelease}')
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "DRY-RUN: POST /repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases"
    echo "$PAYLOAD" | jq '{tag_name, name, target_commitish, prerelease, body:(.body|.[0:80])}'
  else
    CREATED=$(gh_api POST "/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases" \
      -H 'Content-Type: application/json' \
      -d "$PAYLOAD")
    GH_REL_ID=$(echo "$CREATED" | jq -r '.id // empty')
    if [[ -z "$GH_REL_ID" ]]; then
      echo "error: failed to create GitHub release" >&2
      echo "$CREATED" | head -c 600 >&2
      echo >&2
      rm -f "$BODY_FILE"
      exit 1
    fi
    echo "    github id  : ${GH_REL_ID}"
    echo "    html_url   : $(echo "$CREATED" | jq -r '.html_url')"
  fi
else
  echo "    GitHub Release already exists id=${GH_REL_ID} — will sync assets"
fi
rm -f "$BODY_FILE"

# --- assets -------------------------------------------------------------------
ASSET_NAMES=$(echo "$FJ_JSON" | jq -r '.assets[].name')

download_or_local() {
  local name="$1"
  local out="$2"
  if [[ "$LOCAL_DIST" -eq 1 && -f "${DIST_OUT}/${name}" ]]; then
    cp -f "${DIST_OUT}/${name}" "$out"
    return 0
  fi
  # Prefer Forgejo browser_download_url
  local url
  url=$(echo "$FJ_JSON" | jq -r --arg n "$name" '.assets[] | select(.name==$n) | .browser_download_url')
  if [[ -z "$url" || "$url" == "null" ]]; then
    echo "error: no download URL for asset ${name}" >&2
    return 1
  fi
  curl -sS -L "${forgejo_auth[@]}" -o "$out" "$url"
}

upload_gh_asset() {
  local file="$1"
  local name="$2"
  local upload_url
  # Fresh release metadata for upload_url
  local meta
  meta=$(gh_api GET "/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/${GH_REL_ID}")
  upload_url=$(echo "$meta" | jq -r '.upload_url' | sed 's/{?name,label}//')

  # Delete existing asset with same name
  local old_id
  old_id=$(echo "$meta" | jq -r --arg n "$name" '.assets[]? | select(.name==$n) | .id' | head -1)
  if [[ -n "$old_id" && "$old_id" != "null" ]]; then
    echo "    replace asset ${name} (delete id=${old_id})"
    if [[ "$DRY_RUN" -eq 0 ]]; then
      gh_api DELETE "/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/assets/${old_id}" -o /dev/null
    fi
  fi

  echo "    upload ${name} ($(du -h "$file" | awk '{print $1}'))"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "DRY-RUN: POST ${upload_url}?name=${name}"
    return 0
  fi
  local code
  code=$(curl -sS -o /tmp/gh-asset-up.json -w '%{http_code}' \
    "${gh_auth_args[@]}" \
    -H "Accept: application/vnd.github+json" \
    -H "Content-Type: application/octet-stream" \
    -X POST "${upload_url}?name=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$name")" \
    --data-binary @"$file")
  if [[ "$code" != "201" && "$code" != "200" ]]; then
    echo "error: asset upload failed http=${code}" >&2
    head -c 400 /tmp/gh-asset-up.json >&2
    echo >&2
    return 1
  fi
  jq -r '"    ok " + .name + " size=" + (.size|tostring)' /tmp/gh-asset-up.json
}

if [[ "$SKIP_ASSETS" -eq 1 ]]; then
  echo "    skip-assets: yes"
elif [[ "$DRY_RUN" -eq 1 && -z "${GH_REL_ID:-}" ]]; then
  echo "    assets (dry-run, no github id yet):"
  echo "$ASSET_NAMES" | sed 's/^/      - /'
else
  if [[ -z "${GH_REL_ID:-}" ]]; then
    echo "error: no GitHub release id for asset upload" >&2
    exit 1
  fi
  TMPDIR_ASSETS=$(mktemp -d)
  trap 'rm -rf "$TMPDIR_ASSETS"' EXIT
  while IFS= read -r name; do
    [[ -z "$name" ]] && continue
    dest="${TMPDIR_ASSETS}/${name}"
    download_or_local "$name" "$dest"
    upload_gh_asset "$dest" "$name"
  done <<<"$ASSET_NAMES"
fi

echo
echo "OK  tag       : ${TAG}"
echo "OK  forgejo   : ${FORGEJO_BASE_URL}/${FORGEJO_OWNER}/${FORGEJO_REPO}/releases/tag/${TAG}"
echo "OK  github    : https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}/releases/tag/${TAG}"
if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "OK  mode      : dry-run (no mutations)"
fi
