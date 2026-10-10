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

# Copper pack smoke (CU GO 3C golden bar).
#  - customer → quote → accept → order confirm → invoice issue → settle (Mock/SEPA)
#  - delivery service order
#  - aftersales inquiry
#  - if COPPER openForMetalSelection: set tier + assert enabledFamilies (scm+agents OK; no human/digital/sparse)
#
# Usage: BASE=http://127.0.0.1:6060 ./scripts/copper-pack-smoke.sh
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:6060}"
COOKIE=$(mktemp)
trap 'rm -f "$COOKIE"' EXIT

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)"
}

echo "== Copper pack smoke against $BASE =="

curl -s -c "$COOKIE" -b "$COOKIE" -X POST "$BASE/login/authenticate" \
  -d 'username=admin&password=Schn1tz3l3ck3$' -o /dev/null -w 'login=%{http_code}\n'

echo "-- catalog / openForMetalSelection --"
STATUS=$(curl -s -b "$COOKIE" "$BASE/api/setup/status")
echo "$STATUS" | python3 -c "
import json,sys
d=json.load(sys.stdin)
tiers=d.get('tiers') or []
for t in tiers:
    print(' tier', t.get('code'), 'openForMetalSelection=', t.get('openForMetalSelection'))
copper=[t for t in tiers if t.get('code')=='COPPER']
assert copper, 'COPPER missing from catalog'
COPPER_OPEN_FOR_METAL_SELECTION=bool(copper[0].get('openForMetalSelection'))
print('COPPER_OPEN_FOR_METAL_SELECTION', COPPER_OPEN_FOR_METAL_SELECTION)
open('/tmp/copper_open_for_selection','w').write('1' if COPPER_OPEN_FOR_METAL_SELECTION else '0')
"

echo "-- demo seed (idempotent) --"
curl -s -b "$COOKIE" -X POST "$BASE/api/setup/demo-seed" -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(d); print('OK demo seed')"

echo "-- C-S: customer --"
CUST=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/customers" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Copper Pack Smoke $(date +%s)\",\"currencyCode\":\"EUR\"}")
echo "$CUST" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('customer', d['id'])"
CUST_ID=$(echo "$CUST" | json_field "['id']")

echo "-- C-S: quote (service line) --"
QUOTE=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/quotes" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":$CUST_ID,\"name\":\"Copper smoke quote\",\"currencyCode\":\"EUR\",\"lines\":[{\"name\":\"Support hour\",\"quantity\":2,\"unitPriceEur\":95,\"productRefType\":\"SERVICE\"}]}")
echo "$QUOTE" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('quote', d['id'], 'status', d.get('quoteStatus'), 'amt', d.get('amountEur'))"
QID=$(echo "$QUOTE" | json_field "['id']")

echo "-- C-S: send + accept quote --"
curl -s -b "$COOKIE" -X POST "$BASE/api/sales/quotes/$QID/send" -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('sent', d.get('quoteStatus') or d.get('status'))"
ACCEPT=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/quotes/$QID/accept" -H 'Content-Type: application/json' -d '{}')
echo "$ACCEPT" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('order') or d.get('orderId'), d
oid=d.get('orderId') or (d.get('order') or {}).get('id')
print('accepted → order', oid)
open('/tmp/copper_order_id','w').write(str(oid))
"
OID=$(cat /tmp/copper_order_id)

echo "-- C-S: confirm order → finance invoice DRAFT --"
CONF=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/orders/$OID/confirm" -H 'Content-Type: application/json' -d '{}')
echo "$CONF" | python3 -c "
import json,sys
d=json.load(sys.stdin)
inv=d.get('financeInvoiceId')
assert inv, d
print('confirmed financeInvoiceId', inv)
open('/tmp/copper_inv_id','w').write(str(inv))
"
IID=$(cat /tmp/copper_inv_id)

echo "-- C-S: issue invoice --"
curl -s -b "$COOKIE" -X POST "$BASE/api/finance/revenue/invoices/$IID/issue" \
  -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('invoiceStatus') in ('ISSUED','PAID','PART_PAID'), d; print('issued', d.get('invoiceStatus'), d.get('amountEur'))"

echo "-- C-S: settle (SEPA / mock cash) --"
curl -s -b "$COOKIE" -X POST "$BASE/api/finance/revenue/invoices/$IID/settle" \
  -H 'Content-Type: application/json' \
  -d "{\"rail\":\"SEPA\",\"eventId\":\"copper-smoke-$IID-$(date +%s)\"}" \
  | python3 -c "
import json,sys
d=json.load(sys.stdin)
inv=(d.get('invoice') or {})
st=inv.get('invoiceStatus') or d.get('invoiceStatus')
assert st in ('PAID','PART_PAID') or d.get('payment'), d
print('settled', st or 'payment ok', 'je', d.get('journalEntryId'))
"

echo "-- C-S: invoice PDF --"
PDF_CODE=$(curl -s -b "$COOKIE" -o /tmp/copper_invoice.pdf -w '%{http_code}' \
  "$BASE/api/finance/revenue/invoices/$IID/pdf")
echo "pdf_http=$PDF_CODE size=$(wc -c </tmp/copper_invoice.pdf)"
test "$PDF_CODE" = "200"
python3 -c "
data=open('/tmp/copper_invoice.pdf','rb').read()
assert data.startswith(b'%PDF'), data[:20]
assert b'INVOICE' in data or b'Invoice' in data or b'Tj' in data
print('OK invoice PDF')
"

echo "-- PDR-023: FX endpoint --"
curl -s -b "$COOKIE" "$BASE/api/fx/btc?quote=EUR" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('rate') and float(d['rate'])>0, d
assert d.get('quote')=='EUR', d
assert d.get('source') in ('COINGECKO','KRAKEN','COINBASE'), d
print('OK fx', d.get('source'), d.get('rate'), 'stale=', d.get('stale'))
"

echo "-- C-S: settle via BTC wallet address (second invoice) --"
INV2=$(curl -s -b "$COOKIE" -X POST "$BASE/api/finance/revenue/invoices" \
  -H 'Content-Type: application/json' \
  -d "{\"customerName\":\"Copper Wallet Smoke\",\"amountEur\":42,\"issue\":true,\"referenceText\":\"wallet-settle\"}")
IID2=$(echo "$INV2" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['id'])")
curl -s -b "$COOKIE" -X POST "$BASE/api/finance/revenue/invoices/$IID2/settle" \
  -H 'Content-Type: application/json' \
  -d "{\"rail\":\"BTC_WALLET_ADDRESS\",\"walletAddress\":\"bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh\",\"eventId\":\"copper-wallet-$IID2\"}" \
  | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('payment'), d
assert d['payment'].get('rail')=='BTC_WALLET_ADDRESS', d
# payment-time sats may be filled from FX when outbound net works
pay=d['payment']
print('OK wallet-address settle', d.get('journalEntryId'), 'sats', pay.get('amountSats'))
"

echo "-- PDR-023: treasury dual positions --"
curl -s -b "$COOKIE" -X POST "$BASE/api/finance/treasury/seed" \
  -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "
import json,sys
d=json.load(sys.stdin)
items=d.get('items') if isinstance(d, dict) else d
if not isinstance(items, list):
  items=[]
kinds={ (i.get('accountKind') or '') for i in items }
assert 'FIAT_BANK' in kinds and 'BTC_WALLET' in kinds, (kinds, d)
btc=[i for i in items if i.get('accountKind')=='BTC_WALLET'][0]
assert (btc.get('currencyCode') or '') in ('BTC','EUR'), btc
print('OK treasury dual', sorted(kinds), 'btcCur', btc.get('currencyCode'))
"

echo "-- Delivery: service order --"
SO=$(curl -s -b "$COOKIE" -X POST "$BASE/api/delivery/orders" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Copper smoke SO\",\"customerId\":$CUST_ID,\"customerName\":\"Smoke\",\"amountEur\":95,\"quantity\":1,\"lines\":[{\"name\":\"Support hour\",\"quantity\":1,\"unitPriceEur\":95,\"productRefType\":\"SERVICE\"}]}")
echo "$SO" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('service order', d['id'], d.get('fulfillStatus') or d.get('status'))"

echo "-- AfterSales: inquiry --"
INQ=$(curl -s -b "$COOKIE" -X POST "$BASE/api/aftersales/inquiries" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Copper smoke inquiry\",\"kind\":\"REQUEST\",\"customerId\":$CUST_ID,\"channel\":\"EMAIL\",\"description\":\"Pack smoke case\",\"salesOrderId\":$OID}")
echo "$INQ" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('inquiry', d['id'], d.get('caseStatus') or d.get('status'))"

echo "-- Capability list (presence) --"
curl -s -b "$COOKIE" "$BASE/api/capability/projects?max=5" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert 'items' in d or 'total' in d, d; print('capability projects total', d.get('total'))"

if [[ "$(cat /tmp/copper_open_for_selection 2>/dev/null || echo 0)" == "1" ]]; then
  echo "-- E2: set template tier COPPER (full default families) --"
  curl -s -b "$COOKIE" -X POST "$BASE/api/setup/template-tier" \
    -H 'Content-Type: application/json' \
    -d '{"templateTier":"COPPER"}' \
    | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('ok'), d
st=d.get('status') or {}
fam=set(st.get('enabledFamilies') or [])
print('tier', st.get('templateTier'), 'families', sorted(fam))
# G1 scm + G3 agents + G8 delivery (PLUGINS §8)
expected={'core','sales','finance','delivery','aftersales','capability','scm','agents'}
assert fam == expected, ('families', fam, 'expected', expected)
forbidden={'human','digital','strategy','development','asset','grrc','stakeholder'}
assert not (fam & forbidden), ('forbidden families present', fam & forbidden)
print('OK Copper enabledFamilies')
"
  echo "-- E2b: Copper family subset (CU-F3) --"
  curl -s -b "$COOKIE" -X POST "$BASE/api/setup/template-tier" \
    -H 'Content-Type: application/json' \
    -d '{"templateTier":"COPPER","enabledFamilies":["core","sales","finance","delivery"]}' \
    | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('ok'), d
fam=set((d.get('status') or {}).get('enabledFamilies') or [])
assert fam == {'core','sales','finance','delivery'}, fam
print('OK subset', sorted(fam))
"
  echo "-- E2c: Copper rejects human (HR is Bronze+) --"
  CODE=$(curl -s -b "$COOKIE" -o /tmp/copper_forbid.json -w '%{http_code}' \
    -X POST "$BASE/api/setup/template-tier" \
    -H 'Content-Type: application/json' \
    -d '{"templateTier":"COPPER","enabledFamilies":["core","sales","human"]}')
  echo "forbid_http=$CODE body=$(cat /tmp/copper_forbid.json)"
  test "$CODE" = "400"
  echo "-- restore IRON eval (4A) --"
  curl -s -b "$COOKIE" -X POST "$BASE/api/setup/template-tier" \
    -H 'Content-Type: application/json' \
    -d '{"templateTier":"IRON"}' \
    | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('ok'), d; print('restored', (d.get('status') or {}).get('templateTier'))"
else
  echo "-- E2 skip: COPPER not yet open for metal selection --"
fi

echo ""
echo "Copper pack smoke OK (3C + PDF + wallet settle + family subset)."
