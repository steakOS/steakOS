#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The steakOS Authors
#
# KPI registry + feeds smoke (Silver PR-2 / PR-13).
# Usage: BASE=http://127.0.0.1:6060 ./scripts/kpi-api-smoke.sh
#
# Asserts catalogue shape, live ship feed status, Bronze counts-only hero, ar-aging.
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:6060}"
COOKIE=$(mktemp)
trap 'rm -f "$COOKIE"' EXIT

echo "== KPI API smoke against $BASE =="

curl -s -c "$COOKIE" -b "$COOKIE" -X POST "$BASE/login/authenticate" \
  -d 'username=admin&password=Schn1tz3l3ck3$' -o /dev/null -w 'login=%{http_code}\n'

echo "-- GET /api/kpi/catalogue --"
curl -s -b "$COOKIE" "$BASE/api/kpi/catalogue" | python3 -c "
import json,sys
d=json.load(sys.stdin)
meta=d.get('meta') or {}
items=d.get('items') or []
assert meta.get('shipCount')==16, meta
assert meta.get('deferCount')==3, meta
assert len(items)>=16, len(items)
ids={i.get('id') for i in items}
for need in ['KPI-S01','KPI-F02','KPI-W01','KPI-S04','KPI-F05','KPI-I03']:
    assert need in ids, (need, sorted(ids))
defer=[i for i in items if i.get('deferred')]
assert len(defer)>=3, defer
ship=[i for i in items if i.get('ship')]
assert all(i.get('status')=='ready' for i in ship), [(i.get('id'), i.get('status')) for i in ship]
print('OK catalogue ship=%s defer=%s total=%s' % (meta.get('shipCount'), meta.get('deferCount'), len(items)))
"

echo "-- GET /api/kpi/KPI-S01 --"
curl -s -b "$COOKIE" "$BASE/api/kpi/KPI-S01" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('id')=='KPI-S01', d
assert d.get('name'), d
assert d.get('formula'), d
st=(d.get('value') or {}).get('status')
assert st in ('ready','insufficient'), d.get('value')
print('OK show', d.get('id'), d.get('status'), st, (d.get('value') or {}).get('message'))
"

echo "-- GET /api/kpi/KPI-S04 deferred --"
curl -s -b "$COOKIE" "$BASE/api/kpi/KPI-S04" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('deferred') is True, d
assert (d.get('value') or {}).get('status')=='deferred', d.get('value')
print('OK deferred', d.get('id'))
"

echo "-- GET /api/kpi/ar-aging --"
curl -s -b "$COOKIE" "$BASE/api/kpi/ar-aging" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('id')=='KPI-F01', d
val=d.get('value') or {}
assert val.get('status') in ('ready','insufficient'), val
assert 'buckets' in val or val.get('status')=='insufficient', val
print('OK ar-aging status=%s' % val.get('status'))
"

echo "-- GET /api/kpi/dashboard-hero (Bronze → counts-only) --"
curl -s -b "$COOKIE" "$BASE/api/kpi/dashboard-hero" | python3 -c "
import json,sys
d=json.load(sys.stdin)
mode=d.get('mode')
tier=d.get('templateTier')
tiles=d.get('tiles') or []
assert mode in ('counts-only','rich'), d
if tier in (None,'BRONZE','IRON','COPPER') or mode=='counts-only':
    assert mode=='counts-only', d
    assert tiles==[] or len(tiles)==0, tiles
    assert d.get('countsPulse')=='/api/dashboard/ceo', d
    print('OK hero Bronze counts-only')
else:
    assert mode=='rich', d
    assert len(tiles)>=1, tiles
    print('OK hero Silver rich tiles=%s' % len(tiles))
"

echo "-- GET /api/kpi/KPI-ZZZ → 404 --"
CODE=$(curl -s -o /tmp/kpi_zzz.json -w '%{http_code}' -b "$COOKIE" "$BASE/api/kpi/KPI-ZZZ")
test "$CODE" = "404"
python3 -c "import json; d=json.load(open('/tmp/kpi_zzz.json')); assert d.get('code')=='KPI_NOT_FOUND', d"
echo "OK 404 unknown id"

echo "== KPI API smoke OK =="
