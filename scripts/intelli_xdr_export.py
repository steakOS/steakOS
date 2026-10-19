#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Export steakOS XDRs from intelli.db (olympus) to docs/adr and docs/pdr.

Offline cache lives in the private companion tree (steakOS-dev), not the
application monorepo. Policy: companion docs/concepts/INTELLI-DB-ADR-PDR-MIGRATION-CONCEPT.md
  - Overwrite docs/adr + docs/pdr
  - Short filenames: ADR-037.md / PDR-007.md
  - Delete superseded slug files
  - Regenerate README.md indexes
  - Manual run after XDR edits

Env:
  INTELLI_BASE_URL   default http://olympus.local:8000
  INTELLI_USER       default asterix
  INTELLI_PASSWORD   required
  INTELLI_PROJECT_ID default 4
  STEAKOS_DEV_ROOT   optional path to private companion (default: sibling steakOS-dev)
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]


def _docs_root() -> Path:
    env = os.environ.get("STEAKOS_DEV_ROOT")
    if env:
        return Path(env).expanduser().resolve()
    sibling = (REPO.parent / "steakOS-dev").resolve()
    if (sibling / "docs").is_dir():
        return sibling
    return REPO


DOCS_ROOT = _docs_root()
ADR_DIR = DOCS_ROOT / "docs" / "adr"
PDR_DIR = DOCS_ROOT / "docs" / "pdr"

STATUS_OUT = {
    "accepted": "active",
    "proposed": "proposed",
    "deprecated": "deprecated",
}


def login(base: str, user: str, password: str) -> str:
    req = urllib.request.Request(
        base.rstrip("/") + "/auth/login",
        data=json.dumps({"username": user, "password": password}).encode("utf-8"),
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))["token"]


def api_get(base: str, token: str, path: str):
    req = urllib.request.Request(
        base.rstrip("/") + path,
        headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def render_md(row: dict) -> str:
    ref = row.get("reference") or f"ID-{row.get('id')}"
    name = (row.get("name") or ref).strip()
    cat = (row.get("category") or "").upper()
    status = STATUS_OUT.get((row.get("status") or "").lower(), row.get("status") or "proposed")
    xid = row.get("id")
    context = (row.get("context") or "").strip() or "_(empty)_"
    decision = (row.get("decision") or "").strip() or "_(empty)_"
    consequences = (row.get("consequences") or "").strip() or "_(empty)_"
    notes = (row.get("notes") or "").strip()
    changelog = (row.get("changelog") or "").strip()
    parts = [
        f"# {ref} — {name}",
        "",
        f"**Status:** {status}  ",
        f"**Category:** {cat}  ",
        f"**Reference:** `{ref}`  ",
        f"**Project:** steakOS (intelli.db `project_id=4`)  ",
        f"**XDR id:** {xid}  ",
        "",
        "<!-- generated from intelli.db (olympus); SSoT is XDR — do not treat this file as authority -->",
        "",
        "## Context",
        "",
        context,
        "",
        "## Decision",
        "",
        decision,
        "",
        "## Consequences",
        "",
        consequences,
    ]
    if notes:
        parts.extend(["", "## Notes", "", notes])
    if changelog:
        parts.extend(["", "## Change log", "", changelog])
    return "\n".join(parts).rstrip() + "\n"


def short_name(ref: str, category: str) -> str:
    m = re.match(r"(ADR|PDR)-(\d+)$", ref or "", re.I)
    if m:
        return f"{m.group(1).upper()}-{int(m.group(2)):03d}.md"
    # fallback
    safe = re.sub(r"[^A-Za-z0-9._-]+", "-", (ref or "unknown"))
    prefix = "ADR" if category.upper() == "ADR" else "PDR"
    return f"{prefix}-{safe}.md"


def list_slug_victims(directory: Path, keep: set[str]) -> list[Path]:
    """Files to delete: ADR-*.md / PDR-*.md not in keep, excluding README."""
    victims = []
    for p in directory.glob("*.md"):
        if p.name == "README.md":
            continue
        if p.name in keep:
            continue
        if re.match(r"^(ADR|PDR)-", p.name, re.I):
            victims.append(p)
    return sorted(victims)


def write_readme(path: Path, title: str, category: str, rows: list[dict], base_url: str):
    lines = [
        f"# {title}",
        "",
        f"**SSoT:** [intelli.db / olympus]({base_url}) → table `xdr`, `category={category}`, steakOS `project_id=4`.",
        "",
        "Local `.md` files are **exports** for offline/agent use. Edit decisions in olympus, then run:",
        "",
        "```bash",
        "INTELLI_PASSWORD=… python3 scripts/intelli_xdr_export.py --apply",
        "```",
        "",
        "| Code | Status | Title | XDR id |",
        "|------|--------|-------|--------|",
    ]
    for r in sorted(rows, key=lambda x: x.get("reference") or ""):
        ref = r.get("reference") or ""
        st = r.get("status") or ""
        name = (r.get("name") or "").replace("|", "\\|")
        xid = r.get("id")
        fname = short_name(ref, category)
        lines.append(f"| [`{ref}`](./{fname}) | {st} | {name} | {xid} |")
    lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--base-url", default=os.environ.get("INTELLI_BASE_URL", "http://olympus.local:8000"))
    ap.add_argument("--user", default=os.environ.get("INTELLI_USER", "asterix"))
    ap.add_argument("--password", default=os.environ.get("INTELLI_PASSWORD", ""))
    ap.add_argument("--project-id", type=int, default=int(os.environ.get("INTELLI_PROJECT_ID", "4")))
    ap.add_argument(
        "--reference",
        action="append",
        default=[],
        metavar="REF",
        help="Export only these references (repeatable). Skips delete of other local files.",
    )
    ap.add_argument("--apply", action="store_true", help="Write/delete files (default dry-run)")
    args = ap.parse_args()
    dry = not args.apply
    if not args.password:
        print("ERROR: set INTELLI_PASSWORD or --password", file=sys.stderr)
        return 2

    token = login(args.base_url, args.user, args.password)
    data = api_get(args.base_url, token, "/table/xdr?limit=1000")
    rows = [r for r in (data.get("rows") or []) if r.get("project_id") == args.project_id]
    adrs = [r for r in rows if (r.get("category") or "").upper() == "ADR" and r.get("reference")]
    pdrs = [r for r in rows if (r.get("category") or "").upper() == "PDR" and r.get("reference")]

    if args.reference:
        want = set()
        for raw in args.reference:
            m = re.match(r"(ADR|PDR)-(\d+)$", raw.strip(), re.I)
            if m:
                want.add(f"{m.group(1).upper()}-{int(m.group(2)):03d}")
            else:
                want.add(raw.strip())
        def ref_key(r: dict) -> str:
            ref = (r.get("reference") or "").strip()
            m = re.match(r"(ADR|PDR)-(\d+)$", ref, re.I)
            if m:
                return f"{m.group(1).upper()}-{int(m.group(2)):03d}"
            return ref
        adrs = [r for r in adrs if ref_key(r) in want]
        pdrs = [r for r in pdrs if ref_key(r) in want]
        missing = want - {ref_key(r) for r in adrs + pdrs}
        if missing:
            print(f"WARNING: not in PKS: {', '.join(sorted(missing))}", file=sys.stderr)

    plan_write: list[tuple[Path, str]] = []
    keep_adr: set[str] = {"README.md"}
    keep_pdr: set[str] = {"README.md"}

    for r in adrs:
        fn = short_name(r["reference"], "ADR")
        keep_adr.add(fn)
        plan_write.append((ADR_DIR / fn, render_md(r)))
    for r in pdrs:
        fn = short_name(r["reference"], "PDR")
        keep_pdr.add(fn)
        plan_write.append((PDR_DIR / fn, render_md(r)))

    # Selective export: do not delete unrelated local files
    if args.reference:
        del_adr, del_pdr = [], []
    else:
        del_adr = list_slug_victims(ADR_DIR, keep_adr)
        del_pdr = list_slug_victims(PDR_DIR, keep_pdr)

    mode = "DRY-RUN" if dry else "APPLY"
    print(f"=== {mode} export ===")
    print(f"write: {len(plan_write)} (ADR {len(adrs)}, PDR {len(pdrs)})")
    print(f"delete: {len(del_adr) + len(del_pdr)} (adr {len(del_adr)}, pdr {len(del_pdr)})")
    def _rel(p: Path) -> str:
        for root in (DOCS_ROOT, REPO):
            try:
                return str(p.resolve().relative_to(root.resolve()))
            except ValueError:
                continue
        return str(p)

    for p, _ in plan_write[:8]:
        print(f"  WRITE {_rel(p)}")
    if len(plan_write) > 8:
        print(f"  ... +{len(plan_write) - 8} more writes")
    for p in (del_adr + del_pdr)[:12]:
        print(f"  DELETE {_rel(p)}")
    if len(del_adr) + len(del_pdr) > 12:
        print(f"  ... +{len(del_adr) + len(del_pdr) - 12} more deletes")

    if dry:
        print("\nRe-run with --apply to write.")
        return 0

    ADR_DIR.mkdir(parents=True, exist_ok=True)
    PDR_DIR.mkdir(parents=True, exist_ok=True)
    for path, body in plan_write:
        path.write_text(body, encoding="utf-8")
    for path in del_adr + del_pdr:
        path.unlink(missing_ok=True)
    if not args.reference:
        write_readme(ADR_DIR / "README.md", "Architecture Decision Records (ADR)", "ADR", adrs, args.base_url)
        write_readme(PDR_DIR / "README.md", "Product Decision Records (PDR)", "PDR", pdrs, args.base_url)
    print("\nDone.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
