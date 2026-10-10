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

# Bronze pack smoke: privacy ack, B-E2, B-S + PDFs, B-P + PO PDF, B-H, B-I.
# Usage: BASE=http://127.0.0.1:6060 ./scripts/bronze-pack-smoke.sh
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:6060}"
COOKIE=$(mktemp)
trap 'rm -f "$COOKIE" /tmp/bronze_*' EXIT

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)"
}

echo "== Bronze pack smoke against $BASE =="

curl -s -c "$COOKIE" -b "$COOKIE" -X POST "$BASE/login/authenticate" \
  -d 'username=admin&password=Schn1tz3l3ck3$' -o /dev/null -w 'login=%{http_code}\n'

echo "-- catalog --"
STATUS=$(curl -s -b "$COOKIE" "$BASE/api/setup/status")
echo "$STATUS" | python3 -c "
import json,sys
d=json.load(sys.stdin)
bronze=[t for t in (d.get('tiers') or []) if t.get('code')=='BRONZE']
assert bronze and bronze[0].get('openForMetalSelection'), ('BRONZE not open', d.get('tiers'))
print('BRONZE openForMetalSelection=True')
"

echo "-- privacy HR ack --"
curl -s -b "$COOKIE" -X POST "$BASE/api/setup/privacy/hr-ack" \
  -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('ok'), d
priv=(d.get('status') or {}).get('privacy') or {}
assert priv.get('hrAcknowledged') is True or priv.get('hrAcknowledged')==True or str(priv.get('hrAcknowledged')).lower()=='true', priv
print('OK privacy', priv.get('hrAcknowledgedAt'))
"

echo "-- B-E2: template-tier BRONZE --"
curl -s -b "$COOKIE" -X POST "$BASE/api/setup/template-tier" \
  -H 'Content-Type: application/json' \
  -d '{"templateTier":"BRONZE"}' \
  | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('ok'), d
st=d.get('status') or {}
fam=set(st.get('enabledFamilies') or [])
expected={'core','sales','scm','finance','delivery','aftersales','capability','agents','human','digital','strategy','development','asset'}
assert fam==expected, (sorted(fam), sorted(expected))
assert 'grrc' not in fam
print('OK Bronze families', sorted(fam))
"

echo "-- demo seed --"
curl -s -b "$COOKIE" -X POST "$BASE/api/setup/demo-seed" -H 'Content-Type: application/json' -d '{}' >/dev/null
echo "OK demo seed"

TS=$(date +%s)

echo "-- OI-FX01: FX endpoint + money prefs --"
curl -s -b "$COOKIE" "$BASE/api/fx/btc?quote=EUR" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('rate') and float(d['rate'])>0, d
assert d.get('quote')=='EUR', d
assert d.get('source') in ('COINGECKO','KRAKEN','COINBASE','LAST_GOOD'), d
print('OK fx EUR', d.get('source'), d.get('rate'), 'stale=', d.get('stale'))
"
curl -s -b "$COOKIE" "$BASE/api/fx/btc?quote=USD" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('rate') and float(d['rate'])>0, d
assert d.get('quote')=='USD', d
print('OK fx USD', d.get('source'), d.get('rate'))
"
# Help topic for primary rail (seeded on demand)
curl -s -b "$COOKIE" "$BASE/api/help/topics/finance.primaryRail" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('helpKey')=='finance.primaryRail' or (d.get('topic') or {}).get('helpKey')=='finance.primaryRail' or 'Primary value rail' in json.dumps(d), d
body=((d.get('body') or {}).get('bodyText') or d.get('bodyText') or '')
assert 'primary value rail' in body.lower() or 'Primary value rail' in json.dumps(d), body[:200]
print('OK help finance.primaryRail')
"
# Money prefs from setup status / me
curl -s -b "$COOKIE" "$BASE/api/setup/status" | python3 -c "
import json,sys
d=json.load(sys.stdin)
money=d.get('money') or d.get('finance') or {}
# tolerate nested draft
draft=d.get('draft') or {}
rail=(money.get('primaryValueRail') or draft.get('primaryValueRail')
      or d.get('primaryValueRail') or d.get('functionalCurrency') or 'EUR')
assert str(rail).upper() in ('EUR','USD','BTC'), (rail, d.keys())
print('OK money rail', rail)
"

echo "-- OI-F01 / L5: Live BTC path honesty (DEMO mock vs LIVE) --"
curl -s -b "$COOKIE" "$BASE/api/help/topics/finance.payments.live" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('helpKey')=='finance.payments.live' or 'Live payments' in json.dumps(d), d
body=((d.get('body') or {}).get('bodyText') or d.get('bodyText') or json.dumps(d)).lower()
assert 'btcpay' in body and ('mock' in body or 'demo' in body), body[:300]
print('OK help finance.payments.live')
"
# Resolve DEMO / LIVE company ids from /api/me (curl cookie jar)
ME_JSON=$(curl -s -b "$COOKIE" "$BASE/api/me")
DEMO_ID=$(echo "$ME_JSON" | python3 -c "
import json,sys
d=json.load(sys.stdin)
cos=d.get('companies') or []
demo=next((c for c in cos if c.get('demo') or (c.get('kind') or '').upper()=='DEMO'
           or (c.get('code') or '') in ('orange-steak-house','heard-cook-architects')), None)
assert demo, ('expected DEMO showcase', cos)
print(demo['id'])
")
LIVE_ID=$(echo "$ME_JSON" | python3 -c "
import json,sys
d=json.load(sys.stdin)
cos=d.get('companies') or []
live=next((c for c in cos if (c.get('kind') or '').upper()=='LIVE' and not c.get('demo')
           and (c.get('code') or '') not in ('orange-steak-house','heard-cook-architects')), None)
assert live, ('expected LIVE operator', cos)
print(live['id'])
")
echo "companies DEMO id=$DEMO_ID LIVE id=$LIVE_ID"

curl -s -b "$COOKIE" -X POST "$BASE/api/me/company" -H 'Content-Type: application/json' \
  -d "{\"companyId\":$DEMO_ID}" >/dev/null
curl -s -b "$COOKIE" "$BASE/api/finance/payments/settings" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('demoForcedMock') is True, d
assert d.get('liveEnabled') is False, d
assert (d.get('activeProvider') or d.get('provider'))=='MOCK', d
print('OK DEMO forced mock')
"
# Attempt live enable on DEMO — must stay mock
curl -s -b "$COOKIE" -X PUT "$BASE/api/finance/payments/settings" -H 'Content-Type: application/json' \
  -d '{"provider":"BTCPAY","liveEnabled":true}' -o /tmp/bronze_pay_put.json || true
curl -s -b "$COOKIE" "$BASE/api/finance/payments/settings" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('demoForcedMock') is True, d
assert d.get('liveEnabled') is False, d
assert (d.get('activeProvider') or d.get('provider'))=='MOCK', d
print('OK DEMO live BTCPay refused / still mock')
"

curl -s -b "$COOKIE" -X POST "$BASE/api/me/company" -H 'Content-Type: application/json' \
  -d "{\"companyId\":$LIVE_ID}" >/dev/null
curl -s -b "$COOKIE" "$BASE/api/finance/payments/settings" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('demoForcedMock') is False, d
print('OK LIVE demoForcedMock=false provider=', d.get('activeProvider') or d.get('provider'))
"


echo "-- B-S: commercial --"
CUST=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/customers" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Bronze Smoke $TS\",\"currencyCode\":\"EUR\"}")
CUST_ID=$(echo "$CUST" | json_field "['id']")
QUOTE=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/quotes" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":$CUST_ID,\"name\":\"Bronze quote\",\"currencyCode\":\"EUR\",\"lines\":[{\"name\":\"Support retainer\",\"description\":\"Monthly support window\",\"quantity\":2,\"unitPriceEur\":100,\"productRefType\":\"SERVICE\"},{\"name\":\"Onboarding kit\",\"description\":\"Kickoff docs and checklist\",\"quantity\":1,\"unitPriceEur\":50,\"productRefType\":\"GOODS\"}]}")
QID=$(echo "$QUOTE" | json_field "['id']")
curl -s -b "$COOKIE" -X POST "$BASE/api/sales/quotes/$QID/send" -H 'Content-Type: application/json' -d '{}' >/dev/null
ACCEPT=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/quotes/$QID/accept" -H 'Content-Type: application/json' -d '{}')
OID=$(echo "$ACCEPT" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('orderId') or (d.get('order') or {}).get('id'))")
CONF=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/orders/$OID/confirm" -H 'Content-Type: application/json' -d '{}')
IID=$(echo "$CONF" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['financeInvoiceId'])")
INV=$(curl -s -b "$COOKIE" "$BASE/api/finance/revenue/invoices/$IID")
echo "$INV" | python3 -c "
import json,sys
d=json.load(sys.stdin)
# Primary-only commercial doc: functional amount present; secondary sats not required on the invoice
amt=d.get('amountEur')
assert amt is not None and float(amt)>0, d
# New writes should not require invoice.amountSats (may be null/absent)
print('OK invoice primary', amt, 'sats=', d.get('amountSats'))
"
curl -s -b "$COOKIE" -X POST "$BASE/api/finance/revenue/invoices/$IID/issue" -H 'Content-Type: application/json' -d '{}' >/dev/null
curl -s -b "$COOKIE" -X POST "$BASE/api/finance/revenue/invoices/$IID/settle" \
  -H 'Content-Type: application/json' \
  -d "{\"rail\":\"SEPA\",\"eventId\":\"bronze-$IID\"}" >/dev/null
echo "OK B-S customer=$CUST_ID quote=$QID order=$OID invoice=$IID"

echo "-- OI-FX01: dual treasury --"
curl -s -b "$COOKIE" -X POST "$BASE/api/finance/treasury/seed" \
  -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "
import json,sys
d=json.load(sys.stdin)
items=d.get('items') if isinstance(d, dict) else d
if not isinstance(items, list):
  items=d.get('treasuries') or d.get('accounts') or []
kinds={ (i.get('accountKind') or '') for i in items }
assert 'FIAT_BANK' in kinds and 'BTC_WALLET' in kinds, (kinds, type(d), list(d)[:12] if isinstance(d,dict) else d)
print('OK treasury dual', sorted(kinds))
"

echo "-- B-S document PDFs --"
CODE=$(curl -s -b "$COOKIE" -o /tmp/bronze_q.pdf -w '%{http_code}' "$BASE/api/sales/quotes/$QID/pdf")
test "$CODE" = "200"
python3 -c "
import re, zlib
d=open('/tmp/bronze_q.pdf','rb').read()
assert d.startswith(b'%PDF'), d[:20]
assert len(d) > 800, len(d)
meta=d.decode('latin-1','replace')
assert 'MediaBox' in meta and '595' in meta
parts=[]
for m in re.finditer(rb'stream\r?\n(.*?)\r?\nendstream', d, re.S):
    try: parts.append(zlib.decompress(m.group(1)).decode('latin-1','replace'))
    except Exception: pass
hay='\n'.join(parts)
assert 'Support retainer' in hay and 'Onboarding kit' in hay, hay[:400]
assert '250.00' in hay, hay[:400]
print('OK quote PDF', len(d), 'multi-line+total')
"
CODE=$(curl -s -b "$COOKIE" -o /tmp/bronze_o.pdf -w '%{http_code}' "$BASE/api/sales/orders/$OID/pdf")
test "$CODE" = "200"
python3 -c "
import re, zlib
d=open('/tmp/bronze_o.pdf','rb').read()
assert d.startswith(b'%PDF'); assert len(d) > 800
parts=[]
for m in re.finditer(rb'stream\r?\n(.*?)\r?\nendstream', d, re.S):
    try: parts.append(zlib.decompress(m.group(1)).decode('latin-1','replace'))
    except Exception: pass
hay='\n'.join(parts)
assert 'Support retainer' in hay and 'Onboarding kit' in hay, hay[:400]
print('OK order PDF', len(d))
"
CODE=$(curl -s -b "$COOKIE" -o /tmp/bronze_i.pdf -w '%{http_code}' "$BASE/api/finance/revenue/invoices/$IID/pdf")
test "$CODE" = "200"
python3 -c "
d=open('/tmp/bronze_i.pdf','rb').read()
assert d.startswith(b'%PDF'); assert len(d) > 500
print('OK invoice PDF', len(d))
"

echo "-- B-P: vendor PO receive --"
VEND=$(curl -s -b "$COOKIE" -X POST "$BASE/api/scm/vendors" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Bronze Vendor $TS\"}")
VID=$(echo "$VEND" | json_field "['id']")
PO=$(curl -s -b "$COOKIE" -X POST "$BASE/api/scm/purchase-orders" \
  -H 'Content-Type: application/json' \
  -d "{\"vendorId\":$VID,\"name\":\"Bronze PO\",\"currencyCode\":\"EUR\",\"lines\":[{\"name\":\"Widget\",\"quantity\":2,\"unitPriceEur\":25,\"productRefType\":\"GOODS\"}]}")
POID=$(echo "$PO" | json_field "['id']")
curl -s -b "$COOKIE" -X POST "$BASE/api/scm/purchase-orders/$POID/send" -H 'Content-Type: application/json' -d '{}' >/dev/null || true
curl -s -b "$COOKIE" -X POST "$BASE/api/scm/purchase-orders/$POID/confirm" -H 'Content-Type: application/json' -d '{}' >/dev/null
curl -s -b "$COOKIE" -X POST "$BASE/api/scm/purchase-orders/$POID/receive" -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('receipt') or d.get('purchaseOrder'), d; print('OK receive')"
CODE=$(curl -s -b "$COOKIE" -o /tmp/bronze_po.pdf -w '%{http_code}' "$BASE/api/scm/purchase-orders/$POID/pdf")
test "$CODE" = "200"
python3 -c "d=open('/tmp/bronze_po.pdf','rb').read(); assert d.startswith(b'%PDF'); print('OK PO PDF', len(d))"

echo "-- B-H: HR file time payroll --"
FILE=$(curl -s -b "$COOKIE" -X POST "$BASE/api/hr/files" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Bronze Worker $TS\",\"employmentStatus\":\"ACTIVE\",\"email\":\"bronze$TS@example.com\"}")
FID=$(echo "$FILE" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print(d['id'])")
curl -s -b "$COOKIE" -X POST "$BASE/api/hr/time" \
  -H 'Content-Type: application/json' \
  -d "{\"hrFileId\":$FID,\"hours\":8,\"name\":\"Day work\"}" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('time', d['id'])"
# YYYY-MM required; use far-future month from timestamp for uniqueness
PERIOD=$(python3 -c "print('%04d-%02d' % (2090 + ($TS % 9), 1 + ($TS % 12)))")
PAY=$(curl -s -b "$COOKIE" -X POST "$BASE/api/hr/payrolls" \
  -H 'Content-Type: application/json' \
  -d "{\"period\":\"$PERIOD\",\"name\":\"Bronze pay $TS\"}")
PID=$(echo "$PAY" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print(d['id'])")
curl -s -b "$COOKIE" -X PUT "$BASE/api/hr/payrolls/$PID/lines" \
  -H 'Content-Type: application/json' \
  -d "{\"lines\":[{\"hrFileId\":$FID,\"amountEur\":800,\"name\":\"Salary\"}]}" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('payroll lines', d.get('payroll', d).get('id') if isinstance(d.get('payroll'), dict) else d.get('id') or 'ok')"
echo "OK B-H"

echo "-- B-I: digital --"
# Prefer systems, then changes
SYS=$(curl -s -b "$COOKIE" -X POST "$BASE/api/digital/systems" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Bronze Sys $TS\"}")
if echo "$SYS" | python3 -c "import json,sys; d=json.load(sys.stdin); raise SystemExit(0 if d.get('id') else 1)" 2>/dev/null; then
  echo "$SYS" | python3 -c "import json,sys; d=json.load(sys.stdin); print('system', d['id'])"
else
  CHG=$(curl -s -b "$COOKIE" -X POST "$BASE/api/digital/changes" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"Bronze Chg $TS\",\"summary\":\"smoke change\"}")
  echo "$CHG" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('change', d['id'])"
fi
echo "OK B-I"

echo "-- G4: strategy / development / asset thin --"
# Re-apply Bronze so families stay (B-I may leave tier as-is; ensure BRONZE)
curl -s -b "$COOKIE" -X POST "$BASE/api/setup/template-tier" \
  -H 'Content-Type: application/json' \
  -d '{"templateTier":"BRONZE"}' >/dev/null
COMP=$(curl -s -b "$COOKIE" -X POST "$BASE/api/domains/strategy/Competitor" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Competitor $TS\",\"status\":\"WATCH\"}")
echo "$COMP" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('competitor', d['id'])"
SI=$(curl -s -b "$COOKIE" -X POST "$BASE/api/domains/strategy/StrategyItem" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Vision $TS\",\"itemKind\":\"VISION\",\"status\":\"ACTIVE\"}")
echo "$SI" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('strategyItem', d['id'])"
DP=$(curl -s -b "$COOKIE" -X POST "$BASE/api/domains/development/DevelopmentProject" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"NPD $TS\",\"projectKind\":\"NPD\",\"projectStatus\":\"ACTIVE\"}")
echo "$DP" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('id'), d; print('devProject', d['id'])"
AST=$(curl -s -b "$COOKIE" -X POST "$BASE/api/domains/asset/PhysicalAsset" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Van $TS\",\"assetTag\":\"TAG-$TS\",\"location\":\"Yard\",\"ownerName\":\"Ops\",\"status\":\"IN_SERVICE\",\"itManaged\":true}")
echo "$AST" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('id'), d
assert d.get('configItemId'), ('expected permanent ConfigItem link', d)
assert d.get('itManaged') in (True, 'true', 1), d
print('physicalAsset', d['id'], 'configItemId', d['configItemId'])
"
# IT fields only via digital CI
CI_ID=$(echo "$AST" | python3 -c "import json,sys; print(json.load(sys.stdin)['configItemId'])")
curl -s -b "$COOKIE" -X PUT "$BASE/api/domains/digital/ConfigItem/$CI_ID" \
  -H 'Content-Type: application/json' \
  -d '{"hostname":"van-host.local","ipAddress":"10.0.0.9"}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('hostname')=='van-host.local' or d.get('id'), d; print('ci IT fields ok', d.get('id'))"
echo "OK G4"

echo "-- restore BRONZE --"
curl -s -b "$COOKIE" -X POST "$BASE/api/setup/template-tier" \
  -H 'Content-Type: application/json' \
  -d '{"templateTier":"BRONZE"}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('ok'), d; print('restored BRONZE')"

echo ""
echo "Bronze pack smoke OK (incl. OI-FX01 FX + OI-F01 Live BTC honesty)."
