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

# Iron complete smoke (IR-16 / E1–E12 outline).
# Usage: BASE=http://127.0.0.1:6060 ./scripts/iron-complete-smoke.sh
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:6060}"
COOKIE=$(mktemp)
trap 'rm -f "$COOKIE"' EXIT

echo "== Iron complete smoke against $BASE =="

curl -s -c "$COOKIE" -b "$COOKIE" -X POST "$BASE/login/authenticate" \
  -d 'username=admin&password=Schn1tz3l3ck3$' -o /dev/null -w 'login=%{http_code}\n'

echo "-- E2 tiers (Iron always openForMetalSelection; Copper after pack GO) --"
curl -s -b "$COOKIE" "$BASE/api/setup/status" | python3 -c "
import json,sys
d=json.load(sys.stdin)
tiers=d.get('tiers') or []
print('setupComplete', d.get('setupComplete'), 'templateTier', d.get('templateTier'))
for t in tiers:
    print(' tier', t.get('code'), 'openForMetalSelection=', t.get('openForMetalSelection'))
openForMetalSelection=[t['code'] for t in tiers if t.get('openForMetalSelection')]
assert 'IRON' in openForMetalSelection, 'IR-02: Iron must remain open for metal selection'
# Bronze+ must stay locked until those packs productize
for code in ('BRONZE','SILVER','GOLD'):
    assert code not in openForMetalSelection, f'{code} must not be open for metal selection yet'
print('OK metals open for selection', openForMetalSelection)
"

echo "-- E3 demo seed --"
curl -s -b "$COOKIE" -X POST "$BASE/api/setup/demo-seed" -H 'Content-Type: application/json' -d '{}' | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(d)
assert 'message' in d or d.get('skipped') is not None
print('OK demo seed')
"

echo "-- E7 DomainList Task q --"
curl -s -b "$COOKIE" "$BASE/api/domains/core/Task?q=demo&max=10" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print('task total', d.get('total'), 'items', len(d.get('items') or []))
assert 'total' in d and 'items' in d
print('OK domain q')
"

echo "-- catalogue / customers paged --"
curl -s -b "$COOKIE" "$BASE/api/catalogue/items?max=5&offset=0" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print('catalogue total', d.get('total'), 'max', d.get('max'))
assert d.get('max') is not None
print('OK catalogue page')
"
curl -s -b "$COOKIE" "$BASE/api/sales/customers?q=Iron&max=10" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print('customers matching Iron', d.get('total'))
print('OK customer q')
"

echo "-- agents list --"
curl -s -b "$COOKIE" "$BASE/api/agents/agents?max=5" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print('agents total', d.get('total'))
assert 'items' in d
print('OK agents')
"

echo ""
echo "Automated gates OK (E2 tier catalog, E3 seed, E7 q, E10 agents)."
echo "Manual / extend for full golden path:"
echo "  E5 Sales: quote → order → invoice → Mock settle"
echo "  E6 SCM: PO → receive"
echo "  E4 Import: testdata/iron-demo/*.csv via Import hub"
echo "  E9 Stub badges on Finance payroll / soft-close"
echo "  E11 About/wizard Iron alpha copy"
