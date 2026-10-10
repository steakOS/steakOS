#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Rebalance steakOS XDR fields on olympus and ensure changelog minimums.

Field model:
  context      — problem / background only (no Decision/Consequences headings)
  decision     — the locked choice (may contain nested ### subsections)
  consequences — positive / trade-offs / risks / outcomes
  notes        — metadata, owns/boundary, non-goals, related, phased, etc.
  changelog    — history table

Minimum changelog rows:
  - Original record created (document Created: date when known)
  - 2026-08-27 Migrated to intelli.db as SSoT
  - 2026-08-28 Corrected field placement… (when this run changes fields)

Auth: reads Bearer token from /tmp/intelli_token.txt
  (or set INTELLI_TOKEN / login via INTELLI_PASSWORD like the other scripts)

Usage:
  python3 scripts/intelli_xdr_rebalance_fields.py           # apply
  python3 scripts/intelli_xdr_rebalance_fields.py --dry-run
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

TOKEN_PATH = Path("/tmp/intelli_token.txt")
BASE = os.environ.get("INTELLI_BASE_URL", "http://olympus.local:8000")
TODAY = "2026-08-28"
SSOT_DAY = "2026-08-27"
PROJECT_ID = int(os.environ.get("INTELLI_PROJECT_ID", "4"))

# Only split on these top-level headings — nested ### under Decision stay in decision.
TOP_HEADING = re.compile(
    r"(?mi)^(#{2,3})\s+("
    r"Context(?:\s*/\s*Decision)?|"
    r"Decision(?:\s*\([^)]*\))?|"
    r"Consequences(?:\s*\([^)]*\))?|"
    r"Change\s*logs?|"
    r"Owns?\s*/?\s*Boundary|"
    r"Canonical\s+detail|"
    r"Non-?Goals?(?:\s+Of\s+This\s+Pdr)?|"
    r"Notes|"
    r"Related(?:\s+ADRs?|\s+docs?)?|"
    r"Phased\s+Delivery|"
    r"Relationship\s+To\s+Other\s+Adrs?|"
    r"Acceptance\s+Criteria|"
    r"Open\s+Sub-?Decisions|"
    r"Implementation(?:\s+links?)?|"
    r"References?"
    r")\s*$"
)

META_LINE = re.compile(
    r"^\*\*(Status|Confidence|Created|Clarified|Refreshed|Activated|Boundary|"
    r"Product SSoT|Related|Binding|Migration|Formerly|Approved|Decisions locked|"
    r"Layering|Gap closed|Bootstrap|Forbidden|Rejected|Locked clarification)\*\*",
    re.I,
)


def get_token() -> str:
    if os.environ.get("INTELLI_TOKEN"):
        return os.environ["INTELLI_TOKEN"].strip()
    if TOKEN_PATH.exists():
        return TOKEN_PATH.read_text().strip()
    password = os.environ.get("INTELLI_PASSWORD", "")
    if not password:
        raise SystemExit("Need /tmp/intelli_token.txt, INTELLI_TOKEN, or INTELLI_PASSWORD")
    user = os.environ.get("INTELLI_USER", "asterix")
    req = urllib.request.Request(
        BASE.rstrip("/") + "/auth/login",
        data=json.dumps({"username": user, "password": password}).encode(),
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())["token"]


def req(method: str, path: str, token: str, body: dict | None = None):
    data = None if body is None else json.dumps(body).encode()
    r = urllib.request.Request(
        BASE.rstrip("/") + path,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    with urllib.request.urlopen(r, timeout=120) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else {}


def parse_cl(text: str) -> list[tuple[str, str]]:
    rows = []
    for line in (text or "").splitlines():
        m = re.match(r"^\|\s*(\d{4}-\d{2}-\d{2})\s*\|\s*(.+?)\s*\|?\s*$", line.strip())
        if m and m.group(1).lower() != "date" and not set(m.group(2)) <= set("-|:"):
            rows.append((m.group(1), m.group(2).strip().rstrip("|").strip()))
    return rows


def render_cl(rows: list[tuple[str, str]]) -> str:
    seen = set()
    out = []
    for d, n in sorted(rows, key=lambda x: x[0]):
        key = (d, re.sub(r"\s+", " ", n.lower())[:120])
        if key in seen:
            continue
        seen.add(key)
        out.append((d, n.replace("\n", " ").strip()))
    lines = ["| Date | Note |", "|------|------|"]
    for d, n in out:
        lines.append(f"| {d} | {n} |")
    return "\n".join(lines)


def is_ph_dec(t: str) -> bool:
    return (not t) or bool(
        re.search(r"\[See full document|no Decision section extracted|empty decision", t, re.I)
    )


def is_ph_con(t: str) -> bool:
    return (not t) or bool(
        re.search(
            r"\[No Consequences section extracted\]|empty consequences|\(See notes",
            t,
            re.I,
        )
    )


def classify_top(heading: str) -> str:
    h = re.sub(r"^\d+\.?\s*", "", heading.lower().strip())
    h = re.sub(r"\s*\([^)]*\)\s*$", "", h).strip()
    if h.startswith("context") and "decision" in h:
        return "context_decision"
    if h.startswith("context"):
        return "context"
    if "change log" in h or h.startswith("changelog"):
        return "changelog"
    if h.startswith("decision"):
        return "decision"
    if h.startswith("consequence"):
        return "consequences"
    return "notes"


def peel_meta(text: str) -> tuple[list[str], str]:
    if not text:
        return [], ""
    if re.search(r"\*\*Status:\*\*", text):
        paras = re.split(r"\n\s*\n", text, maxsplit=1)
        if re.search(r"\*\*Status:\*\*", paras[0]):
            rest = paras[1].strip() if len(paras) > 1 else ""
            return [paras[0].strip()], rest
    meta, body = [], []
    for line in text.splitlines():
        if META_LINE.match(line.strip()) or line.strip().startswith("**Status:**"):
            meta.append(line.strip())
        else:
            body.append(line)
    return meta, "\n".join(body).strip()


def split_top_level(text: str):
    """Split only on known top-level headings; keep nested ### inside sections."""
    matches = list(TOP_HEADING.finditer(text or ""))
    preamble = (text or "")[: matches[0].start()].strip() if matches else (text or "").strip()
    sections = []
    for i, m in enumerate(matches):
        title = m.group(2).strip()
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text or "")
        sections.append((title, (text or "")[start:end].strip()))
    return preamble, sections


def rebalance(row: dict) -> tuple[dict, bool, list[str]]:
    ctx = (row.get("context") or "").strip()
    dec = (row.get("decision") or "").strip()
    con = (row.get("consequences") or "").strip()
    notes = (row.get("notes") or "").strip()
    changelog = (row.get("changelog") or "").strip()
    reasons: list[str] = []
    field_changed = False

    # Peel Decision/Consequences still sitting inside context
    if re.search(r"(?mi)^#{2,3}\s+(Decision|Consequences)\b", ctx):
        pre, secs = split_top_level(ctx)
        meta, pre_body = peel_meta(pre)
        ext_ctx, ext_dec, ext_con, ext_notes, ext_cl = [], [], [], [], []
        if meta:
            ext_notes.append("## Record metadata\n\n" + "\n\n".join(meta))
        if pre_body:
            ext_ctx.append(pre_body)
        for heading, body in secs:
            kind = classify_top(heading)
            if kind == "context":
                ext_ctx.append(body)
            elif kind == "context_decision":
                ext_ctx.append(body)
                ext_dec.append(body)
            elif kind == "decision":
                ext_dec.append(body)
            elif kind == "consequences":
                ext_con.append(body)
            elif kind == "changelog":
                ext_cl.append(body)
            else:
                ext_notes.append(f"## {heading}\n\n{body}".strip())
        new_ctx = "\n\n".join(ext_ctx).strip()
        if new_ctx != ctx:
            ctx = new_ctx
            field_changed = True
            reasons.append("split_nested_ctx")
        if ext_dec and (is_ph_dec(dec) or len("\n\n".join(ext_dec)) > len(dec)):
            dec = "\n\n".join(ext_dec).strip()
            field_changed = True
            reasons.append("decision_from_ctx")
        if ext_con and (is_ph_con(con) or len("\n\n".join(ext_con)) > len(con)):
            con = "\n\n".join(ext_con).strip()
            field_changed = True
            reasons.append("consequences_from_ctx")
        for block in ext_notes:
            if block and block not in notes:
                notes = f"{notes}\n\n{block}".strip() if notes else block
                field_changed = True
                reasons.append("notes_from_ctx")
        for block in ext_cl:
            if block and block not in changelog:
                changelog = f"{changelog}\n\n{block}".strip() if changelog else block
                field_changed = True

    # Status meta still leading context
    if re.match(r"^\*\*Status:\*\*", ctx.lstrip()):
        paras = re.split(r"\n\s*\n", ctx, maxsplit=1)
        block = "## Record metadata\n\n" + paras[0].strip()
        rest = paras[1].strip() if len(paras) > 1 else ""
        if block not in notes:
            notes = f"{notes}\n\n{block}".strip() if notes else block
        ctx = rest
        field_changed = True
        reasons.append("peel_meta")

    # Unwrap lone ### Context wrapper
    m = re.match(r"^###\s+Context\s*\n+(.*)$", ctx, re.S | re.I)
    if m and not re.search(r"(?m)^###\s+", m.group(1)):
        ctx = m.group(1).strip()
        field_changed = True
        reasons.append("unwrap_context")

    for k in ("ctx", "dec", "con"):
        pass
    ctx = re.sub(r"\n*---\s*$", "", ctx).strip()
    dec = re.sub(r"\n*---\s*$", "", dec).strip()
    con = re.sub(r"\n*---\s*$", "", con).strip()

    # Changelog minimums — prefer Created: from notes metadata
    cl_rows = parse_cl(changelog)
    meta_created = None
    mm = re.search(r"\*\*Created:\*\*\s*(\d{4}-\d{2}-\d{2})", notes)
    if mm:
        meta_created = mm.group(1)
    created = meta_created or (row.get("created") or "")[:10]

    if created:
        if not any("Original record created" in n for _, n in cl_rows):
            cl_rows.append((created, "Original record created"))
            field_changed = True
            reasons.append("cl_created")
        else:
            fixed = []
            for d, n in cl_rows:
                if "Original record created" in n and d != created:
                    fixed.append((created, "Original record created"))
                    field_changed = True
                    reasons.append(f"cl_fix_created:{d}->{created}")
                else:
                    fixed.append((d, n))
            cl_rows = fixed

    if not any(re.search(r"intelli\.db|ssot|single source", n, re.I) for _, n in cl_rows):
        cl_rows.append((SSOT_DAY, "Migrated to intelli.db as SSoT (steakOS project)"))
        field_changed = True
        reasons.append("cl_ssot")

    if field_changed and not any(
        d == TODAY and re.search(r"field placement|correct|restor", n, re.I) for d, n in cl_rows
    ):
        cl_rows.append(
            (
                TODAY,
                "Corrected field placement / restored truncated fields from source documents",
            )
        )

    new_cl = render_cl(cl_rows)
    if new_cl != changelog:
        changelog = new_cl
        field_changed = True

    data = {
        "name": row["name"],
        "context": ctx or "(empty context)",
        "decision": dec or "[No Decision section extracted]",
        "consequences": con or "[No Consequences section extracted]",
        "notes": notes or None,
        "changelog": changelog,
    }
    content_changed = any(
        [
            data["context"] != (row.get("context") or "").strip(),
            data["decision"] != (row.get("decision") or "").strip(),
            data["consequences"] != (row.get("consequences") or "").strip(),
            (data.get("notes") or "") != (row.get("notes") or "").strip(),
            parse_cl(data["changelog"]) != parse_cl(row.get("changelog") or ""),
        ]
    )
    return data, content_changed, reasons


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    token = get_token()

    rows = [r for r in req("GET", "/table/xdr?limit=1000", token)["rows"] if r.get("project_id") == PROJECT_ID]
    updated = 0
    errors = []
    for r in rows:
        try:
            data, changed, reasons = rebalance(r)
            if not changed:
                continue
            print(("DRY " if args.dry_run else "") + f"update {r.get('reference')}: {reasons}")
            if not args.dry_run:
                req("PUT", f"/table/xdr/{r['id']}", token, {"data": data, "tags": []})
            updated += 1
        except Exception as e:
            errors.append(f"{r.get('reference')}: {e}")

    print(f"\nupdated={updated} errors={len(errors)}")
    for e in errors[:10]:
        print("ERR", e)

    rows = [r for r in req("GET", "/table/xdr?limit=1000", token)["rows"] if r.get("project_id") == PROJECT_ID]
    nested = sum(
        1
        for r in rows
        if re.search(r"(?mi)^#{2,3}\s+(Decision|Consequences)\b", r.get("context") or "")
    )
    status_ctx = sum(1 for r in rows if re.match(r"^\*\*Status:\*\*", (r.get("context") or "").lstrip()))
    ph_dec = sum(1 for r in rows if is_ph_dec(r.get("decision") or ""))
    ph_con = sum(1 for r in rows if is_ph_con(r.get("consequences") or ""))
    empty_cl = sum(1 for r in rows if not (r.get("changelog") or "").strip())
    miss_ssot = sum(
        1
        for r in rows
        if not any(re.search(r"intelli\.db|ssot|single source", n, re.I) for _, n in parse_cl(r.get("changelog") or ""))
    )
    miss_created = sum(
        1
        for r in rows
        if not any("Original record created" in n for _, n in parse_cl(r.get("changelog") or ""))
    )
    print(
        f"verify nested={nested} status_in_ctx={status_ctx} ph_dec={ph_dec} ph_con={ph_con} "
        f"empty_cl={empty_cl} miss_ssot={miss_ssot} miss_created={miss_created}"
    )
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
