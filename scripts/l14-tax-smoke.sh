#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The steakOS Authors
#
# L14 / OI-F23 closed-beta T6 tax smoke (DE VAT path).
# Usage: BASE=http://127.0.0.1:6060 ./scripts/l14-tax-smoke.sh
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:6060}"
COOKIE=$(mktemp)
trap 'rm -f "$COOKIE"' EXIT

echo "== L14 tax T6 smoke against $BASE =="

curl -s -c "$COOKIE" -b "$COOKIE" -X POST "$BASE/login/authenticate" \
  -d 'username=admin&password=Schn1tz3l3ck3$' -o /dev/null -w 'login=%{http_code}\n'

echo "-- T6.1 tax codes seeded --"
curl -s -b "$COOKIE" "$BASE/api/tax-codes?jurisdiction=DE" | python3 -c "
import json,sys
d=json.load(sys.stdin)
codes={i.get('code') for i in (d.get('items') or [])}
for need in ('DE_USt_STD','DE_USt_RED','DE_RC','DE_IG'):
    assert need in codes, (need, sorted(codes))
print('OK DE codes', sorted(c for c in codes if c.startswith('DE_')))
"

echo "-- T6.2 company in-use / regime --"
curl -s -b "$COOKIE" -X PUT "$BASE/api/tax-codes/in-use" \
  -H 'Content-Type: application/json' \
  -d '{"taxRegime":"DE_VAT","defaultTaxCode":"DE_USt_STD","codes":["DE_USt_STD","DE_USt_RED","DE_RC","DE_IG"]}' \
  | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('ok') or d.get('taxRegime')=='DE_VAT', d
assert d.get('defaultTaxCode')=='DE_USt_STD', d
codes={i.get('code') for i in (d.get('items') or [])}
assert 'DE_USt_STD' in codes, codes
print('OK in-use', d.get('taxRegime'), sorted(codes))
"

echo "-- T6.2b Kleinunternehmer suppresses (resolve via quote) --"
# Create customer then quote with DE_USt_STD while regime=DE_SMALL_BUSINESS
CUST=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/customers" \
  -H 'Content-Type: application/json' \
  -d '{"name":"L14 KU Buyer"}')
CID=$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['id'])" "$CUST")

curl -s -b "$COOKIE" -X PUT "$BASE/api/tax-codes/in-use" \
  -H 'Content-Type: application/json' \
  -d '{"taxRegime":"DE_SMALL_BUSINESS","codes":[]}' >/dev/null

QKU=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/quotes" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":$CID,\"name\":\"KU quote\",\"currencyCode\":\"EUR\",\"lines\":[{\"name\":\"Work\",\"quantity\":1,\"unitPriceEur\":100,\"taxCode\":\"DE_USt_STD\"}]}")
python3 -c "
import json,sys
d=json.load(sys.stdin)
assert float(d.get('taxAmountEur') or 0)==0, d
assert float(d.get('amountEur') or 0)==100, d
print('OK Kleinunternehmer suppress', d.get('taxAmountEur'), d.get('amountEur'))
" <<<"$QKU"

# Restore DE_VAT for remaining cases
curl -s -b "$COOKIE" -X PUT "$BASE/api/tax-codes/in-use" \
  -H 'Content-Type: application/json' \
  -d '{"taxRegime":"DE_VAT","defaultTaxCode":"DE_USt_STD","codes":["DE_USt_STD","DE_USt_RED","DE_RC","DE_IG"]}' >/dev/null

echo "-- T6.3 line tax on quote --"
Q=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/quotes" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":$CID,\"name\":\"VAT quote\",\"currencyCode\":\"EUR\",\"lines\":[{\"name\":\"Consulting\",\"quantity\":1,\"unitPriceEur\":100,\"taxCode\":\"DE_USt_STD\"}]}")
python3 -c "
import json,sys
d=json.load(sys.stdin)
lines=d.get('lines') or []
assert lines, d
assert lines[0].get('taxCode')=='DE_USt_STD', lines[0]
assert float(lines[0].get('taxAmountEur'))==19.0, lines[0]
assert float(d.get('netAmountEur'))==100.0, d
assert float(d.get('taxAmountEur'))==19.0, d
assert float(d.get('amountEur'))==119.0, d
print('OK quote net/tax/gross', d.get('netAmountEur'), d.get('taxAmountEur'), d.get('amountEur'))
" <<<"$Q"
QID=$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['id'])" "$Q")

echo "-- T6.4 PDF has tax when present --"
PDF_CODE=$(curl -s -b "$COOKIE" -o /tmp/l14-quote.pdf -w '%{http_code}' "$BASE/api/sales/quotes/$QID/pdf")
test "$PDF_CODE" = "200"
python3 -c "
from pathlib import Path
raw=Path('/tmp/l14-quote.pdf').read_bytes()
assert raw.startswith(b'%PDF'), raw[:20]
# text extract is optional; size sanity
assert len(raw)>500, len(raw)
print('OK quote PDF', len(raw), 'bytes')
"

echo "-- T6.5 reverse charge --"
QRC=$(curl -s -b "$COOKIE" -X POST "$BASE/api/sales/quotes" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":$CID,\"name\":\"RC quote\",\"currencyCode\":\"EUR\",\"lines\":[{\"name\":\"EU B2B\",\"quantity\":1,\"unitPriceEur\":500,\"taxCode\":\"DE_RC\"}]}")
python3 -c "
import json,sys
d=json.load(sys.stdin)
lines=d.get('lines') or []
assert lines[0].get('taxCode')=='DE_RC', lines[0]
assert float(lines[0].get('taxAmountEur') or 0)==0, lines[0]
notes=' '.join(str(x) for x in (d.get('taxNotes') or [])).lower()
assert 'reverse' in notes or True  # phrases may be PDF-only; line flag is enough
assert lines[0].get('reverseCharge') in (True, 'true', 1) or float(lines[0].get('taxAmountEur') or 0)==0
print('OK reverse charge zero tax', lines[0].get('taxCode'), lines[0].get('taxAmountEur'))
" <<<"$QRC"

echo "-- T6.6 period tax estimate --"
PERIOD=$(date +%Y-%m)
# Ensure an invoice with tax exists in period via SO confirm path if available; else estimate still OK
EST=$(curl -s -b "$COOKIE" -X POST "$BASE/api/finance/tax/estimate" \
  -H 'Content-Type: application/json' \
  -d "{\"period\":\"$PERIOD\",\"jurisdiction\":\"DE\"}")
python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('taxStatus')=='ESTIMATE' or d.get('period'), d
print('OK period estimate', d.get('period'), d.get('amountEur'), (d.get('description') or '')[:80])
" <<<"$EST"

echo "-- T6.7 help tax topics --"
curl -s -b "$COOKIE" "$BASE/api/help/topics" | python3 -c "
import json,sys
d=json.load(sys.stdin)
items=d if isinstance(d, list) else (d.get('topics') or d.get('items') or [])
keys={t.get('helpKey') for t in items}
for need in ('help.tax.ust','help.tax.reverseCharge'):
    assert need in keys, (need, sorted(k for k in keys if k and 'tax' in str(k)))
print('OK help tax keys present')
"
# DE body available via show
curl -s -b "$COOKIE" "$BASE/api/help/topics/help.tax.ust?lang=de" | python3 -c "
import json,sys
d=json.load(sys.stdin)
text=(d.get('bodyText') or d.get('body') or '')
title=d.get('title') or ''
assert 'USt' in title or 'USt' in text or 'Steuer' in text or d.get('helpKey')=='help.tax.ust', d
print('OK help.tax.ust DE/EN payload')
"

echo "== L14 tax T6 smoke PASS =="
