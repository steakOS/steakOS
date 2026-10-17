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

# Silver pack smoke (PR-16 / masterplan Si-* + DoD D2 gates).
# Covers while SILVER selection is still closed:
#   - setup status flags (pluginDepth, messaging defaults off, SILVER locked)
#   - Advanced gate 403 on Bronze (OI-SI05)
#   - GRRC / Stakeholder 403 on Bronze (G7)
#   - KPI catalogue + Bronze counts-only hero
#   - abbreviated Si-B / Si-I / Si-A on Bronze families
# When OPEN_FOR includes SILVER (PR-17+), also runs Si-E2 + Silver surfaces.
#
# Usage:
#   BASE=http://127.0.0.1:6060 ./scripts/silver-pack-smoke.sh
#   SKIP_SI_B=1 ./scripts/silver-pack-smoke.sh   # skip light commercial Si-B
# Companion: steakOS-dev/testing/SILVER-PR16-PACK-SMOKE.md
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:6060}"
COOKIE=$(mktemp)
TMPDIR_SMOKE=$(mktemp -d)
trap 'rm -f "$COOKIE"; rm -rf "$TMPDIR_SMOKE"' EXIT

PASS=0
FAIL=0
SKIP=0
SILVER_OPEN=0

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)"
}

expect_http() {
  # expect_http LABEL METHOD URL [json-body] EXPECTED_CODE [python-assert-on-body]
  local label="$1" method="$2" url="$3"
  shift 3
  local body="" code_expect="" pyassert=""
  if [[ "${1:-}" == \{* ]]; then
    body="$1"; shift
  fi
  code_expect="${1:-200}"; shift || true
  pyassert="${1:-}"
  local out="$TMPDIR_SMOKE/resp.json"
  local args=(-s -b "$COOKIE" -c "$COOKIE" -o "$out" -w '%{http_code}' -X "$method" "$url")
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  local code
  code=$(curl "${args[@]}")
  if [[ "$code" != "$code_expect" ]]; then
    echo "FAIL $label: expected HTTP $code_expect got $code body=$(head -c 240 "$out")"
    FAIL=$((FAIL + 1))
    return 1
  fi
  if [[ -n "$pyassert" ]]; then
    if ! python3 -c "
import json,sys
d=json.load(open('$out'))
$pyassert
" 2>"$TMPDIR_SMOKE/assert.err"; then
      echo "FAIL $label: body assert failed ($(cat "$TMPDIR_SMOKE/assert.err")) body=$(head -c 240 "$out")"
      FAIL=$((FAIL + 1))
      return 1
    fi
  fi
  echo "OK $label (HTTP $code)"
  PASS=$((PASS + 1))
  return 0
}

echo "== Silver pack smoke against $BASE =="

curl -s -c "$COOKIE" -b "$COOKIE" -X POST "$BASE/login/authenticate" \
  -d 'username=admin&password=Schn1tz3l3ck3$' -o /dev/null -w 'login=%{http_code}\n'

# ---------------------------------------------------------------------------
# Setup status flags (W1 / PR-3 keys; selection honesty)
# ---------------------------------------------------------------------------
echo "-- setup status flags --"
STATUS=$(curl -s -b "$COOKIE" "$BASE/api/setup/status")
echo "$STATUS" | python3 -c "
import json,sys
d=json.load(sys.stdin)
open('/tmp/silver_smoke_status.json','w').write(json.dumps(d))
tiers={t.get('code'): t for t in (d.get('tiers') or [])}
bronze=tiers.get('BRONZE') or {}
silver=tiers.get('SILVER') or {}
gold=tiers.get('GOLD') or {}
assert bool(bronze.get('openForMetalSelection')) is True, ('BRONZE must stay open', bronze)
# GOLD stays locked through Silver wave; SILVER may open at PR-17
assert bool(gold.get('openForMetalSelection')) is False, gold
silver_open = bool(silver.get('openForMetalSelection'))
pd=d.get('pluginDepth') or {}
assert 'sales' in pd and 'finance' in pd, ('pluginDepth missing', pd)
msg=d.get('messaging') or {}
assert 'emailEnabled' in msg and 'telegramEnabled' in msg, ('messaging flags missing', msg)
# Defaults off (lab tenant should not have messaging claimed on for pack smoke)
assert msg.get('emailEnabled') is False, ('email default should be off', msg)
assert msg.get('telegramEnabled') is False, ('telegram default should be off', msg)
print('templateTier', d.get('templateTier'))
print('pluginDepth', pd)
print('messaging', msg)
print('SILVER openForMetalSelection', silver_open)
open('/tmp/silver_smoke_open','w').write('1' if silver_open else '0')
"
PASS=$((PASS + 1))
echo "OK status flags (SILVER locked, messaging defaults off, pluginDepth present)"
SILVER_OPEN=$(cat /tmp/silver_smoke_open)

# Ensure tenant is on Bronze for gate checks
echo "-- ensure BRONZE template-tier --"
expect_http "setTemplateTier BRONZE" POST "$BASE/api/setup/template-tier" \
  '{"templateTier":"BRONZE"}' 200 \
  "assert d.get('ok') is True, d; st=d.get('status') or {}; assert st.get('templateTier')=='BRONZE', st; fam=set(st.get('enabledFamilies') or []); assert 'grrc' not in fam and 'stakeholder' not in fam, sorted(fam)"

echo "-- SILVER template-tier while locked --"
if [[ "$SILVER_OPEN" == "1" ]]; then
  echo "SKIP SILVER locked check (selection already open)"
  SKIP=$((SKIP + 1))
else
  expect_http "SILVER METAL_NOT_OPEN" POST "$BASE/api/setup/template-tier" \
    '{"templateTier":"SILVER"}' 400 \
    "assert d.get('code')=='METAL_NOT_OPEN_FOR_SELECTION', d"
fi

# ---------------------------------------------------------------------------
# Advanced gate 403 on Bronze (OI-SI05)
# ---------------------------------------------------------------------------
echo "-- Advanced 403 on Bronze --"
for path in \
  /api/sales/pricelists \
  /api/sales/crm/opportunities \
  /api/finance/collections \
  /api/finance/match
do
  expect_http "Advanced refuse $path" GET "$BASE$path" 403 \
    "assert d.get('code')=='ADVANCED_REQUIRES_SILVER', d; assert (d.get('tier') or '').upper()=='BRONZE', d"
done

# ---------------------------------------------------------------------------
# GRRC / Stakeholder 403 on Bronze (G7)
# ---------------------------------------------------------------------------
echo "-- GRRC / Stakeholder 403 on Bronze --"
expect_http "GRRC risks refuse" GET "$BASE/api/grrc/risks" 403 \
  "assert d.get('code')=='GRRC_REQUIRES_SILVER', d"
expect_http "Stakeholder parties refuse" GET "$BASE/api/stakeholder/parties" 403 \
  "assert d.get('code')=='STAKEHOLDER_REQUIRES_SILVER', d"

# ---------------------------------------------------------------------------
# KPI catalogue + hero (Bronze → counts-only)
# ---------------------------------------------------------------------------
echo "-- KPI catalogue / hero --"
expect_http "KPI catalogue" GET "$BASE/api/kpi/catalogue" 200 \
  "meta=d.get('meta') or {}; assert meta.get('shipCount')==16, meta; assert meta.get('deferCount')>=3, meta; items=d.get('items') or []; assert len(items)>=16, len(items); ids={i.get('id') for i in items}; assert 'KPI-S01' in ids and 'KPI-F02' in ids, sorted(ids)[:12]"
expect_http "KPI hero Bronze counts-only" GET "$BASE/api/kpi/dashboard-hero" 200 \
  "assert d.get('mode')=='counts-only', d; assert (d.get('tiles') or [])==[] or len(d.get('tiles') or [])==0, d; assert d.get('countsPulse')=='/api/dashboard/ceo', d"

# ---------------------------------------------------------------------------
# Messaging defaults (pack-options read-back; leave off)
# ---------------------------------------------------------------------------
echo "-- messaging disabled default --"
# Re-assert from status; optional pack-options no-op keep-off
expect_http "pack-options keep messaging off" POST "$BASE/api/setup/pack-options" \
  '{"messaging":{"emailEnabled":false,"telegramEnabled":false}}' 200 \
  "assert d.get('ok') is True, d; msg=((d.get('status') or {}).get('messaging') or {}); assert msg.get('emailEnabled') is False and msg.get('telegramEnabled') is False, msg"

# ---------------------------------------------------------------------------
# Si-B light (Bronze commercial still works) — optional skip
# ---------------------------------------------------------------------------
TS=$(date +%s)
if [[ "${SKIP_SI_B:-0}" == "1" ]]; then
  echo "SKIP Si-B (SKIP_SI_B=1)"
  SKIP=$((SKIP + 1))
else
  echo "-- Si-B light: customer create on Bronze --"
  CUST=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/customers" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"Silver Smoke $TS\",\"currencyCode\":\"EUR\"}")
  echo "$CUST" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('OK Si-B customer', d['id'])"
  PASS=$((PASS + 1))
fi

# ---------------------------------------------------------------------------
# Si-I: digital CI / system
# ---------------------------------------------------------------------------
echo "-- Si-I: digital system --"
SYS=$(curl -s -b "$COOKIE" -X POST "$BASE/api/digital/systems" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Silver Si-I $TS\"}")
if echo "$SYS" | python3 -c "import json,sys; d=json.load(sys.stdin); raise SystemExit(0 if d.get('id') else 1)" 2>/dev/null; then
  echo "$SYS" | python3 -c "import json,sys; d=json.load(sys.stdin); print('OK Si-I system', d['id'])"
  PASS=$((PASS + 1))
else
  CHG=$(curl -s -b "$COOKIE" -X POST "$BASE/api/digital/changes" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"Silver Si-I chg $TS\",\"summary\":\"smoke change\"}")
  echo "$CHG" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('OK Si-I change', d['id'])"
  PASS=$((PASS + 1))
fi

# ---------------------------------------------------------------------------
# Si-A: agents list + open approvals (HITL surface present)
# ---------------------------------------------------------------------------
echo "-- Si-A: agents + approvals --"
expect_http "agents list" GET "$BASE/api/agents/agents" 200 \
  "assert 'items' in d or isinstance(d, list) or d.get('total') is not None or 'id' in d, list(d)[:12] if isinstance(d,dict) else d"
expect_http "approvals open" GET "$BASE/api/agents/approvals/open" 200 \
  "assert 'items' in d or isinstance(d, list) or d.get('total') is not None, d"

# ---------------------------------------------------------------------------
# Si-E2 + Silver surfaces — only when selection open (PR-17+)
# ---------------------------------------------------------------------------
echo "-- Si-E2 / Silver surfaces --"
if [[ "$SILVER_OPEN" != "1" ]]; then
  echo "SKIP Si-E2: SILVER not open for metal selection (residual for PR-17)"
  echo "  Frontend preview (?previewSilver=1) is UI-only and does not unlock API gates."
  SKIP=$((SKIP + 1))
else
  expect_http "Si-E2 setTemplateTier SILVER" POST "$BASE/api/setup/template-tier" \
    '{"templateTier":"SILVER"}' 200 \
    "assert d.get('ok') is True, d; st=d.get('status') or {}; assert st.get('templateTier')=='SILVER', st; fam=set(st.get('enabledFamilies') or []); assert 'grrc' in fam and 'stakeholder' in fam and 'digital' in fam and 'agents' in fam, sorted(fam); pd=st.get('pluginDepth') or {}; assert pd.get('sales')=='ADVANCED' and pd.get('finance')=='ADVANCED', pd; msg=st.get('messaging') or {}; assert msg.get('emailEnabled') is False and msg.get('telegramEnabled') is False, msg"

  expect_http "Silver pricelists list" GET "$BASE/api/sales/pricelists" 200 \
    "assert 'items' in d or isinstance(d, list) or d.get('total') is not None, d"
  expect_http "Silver collections" GET "$BASE/api/finance/collections" 200 \
    "assert isinstance(d, (dict, list)), d"
  expect_http "Silver GRRC risks" GET "$BASE/api/grrc/risks" 200 \
    "assert 'items' in d or isinstance(d, list) or d.get('total') is not None, d"
  expect_http "Silver Stakeholder parties" GET "$BASE/api/stakeholder/parties" 200 \
    "assert 'items' in d or isinstance(d, list) or d.get('total') is not None, d"
  expect_http "KPI hero Silver rich" GET "$BASE/api/kpi/dashboard-hero" 200 \
    "assert d.get('mode')=='rich', d; assert len(d.get('tiles') or [])>=1, d"

  # Restore Bronze so lab stays on open metal
  expect_http "restore BRONZE" POST "$BASE/api/setup/template-tier" \
    '{"templateTier":"BRONZE"}' 200 \
    "assert (d.get('status') or {}).get('templateTier')=='BRONZE', d"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "Silver pack smoke summary: PASS=$PASS FAIL=$FAIL SKIP=$SKIP SILVER_OPEN=$SILVER_OPEN"
if [[ "$FAIL" -gt 0 ]]; then
  echo "Silver pack smoke FAILED"
  exit 1
fi
echo "Silver pack smoke OK (Bronze gates + KPI + messaging defaults; Si-E2 deferred until PR-17 unless SILVER open)."
exit 0
