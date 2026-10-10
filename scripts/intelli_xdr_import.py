#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Import steakOS ADR/PDR markdown into intelli.db (olympus) XDR table.

Offline markdown lives in the private companion (steakOS-dev).
SSoT policy: companion docs/concepts/INTELLI-DB-ADR-PDR-MIGRATION-CONCEPT.md.

Env:
  INTELLI_BASE_URL   default http://olympus.local:8000
  INTELLI_USER       default asterix
  INTELLI_PASSWORD   required unless --password
  INTELLI_PROJECT_ID default 4 (steakOS)
  STEAKOS_DEV_ROOT   optional path to private companion (default: sibling steakOS-dev)

Usage:
  python3 scripts/intelli_xdr_import.py --dry-run
  python3 scripts/intelli_xdr_import.py --apply
  python3 scripts/intelli_xdr_import.py --apply --only pdr
  python3 scripts/intelli_xdr_import.py --apply --only adr
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
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

# ADR-030: prefer short export name; keep legacy slug as fallback only
ADR_030_PREFERRED = "ADR-030.md"
ADR_030_LEGACY = "ADR-030-services-model.md"

STATUS_MAP = {
    "active": "accepted",
    "activated": "accepted",
    "locked": "accepted",
    "accepted": "accepted",
    "proposed": "proposed",
    "draft": "proposed",
    "open": "proposed",
    "open for walkthrough": "proposed",
    "superseded": "deprecated",
    "deprecated": "deprecated",
    "withdrawn": "deprecated",
}


class Api:
    def __init__(self, base: str, token: str):
        self.base = base.rstrip("/")
        self.token = token

    def _req(self, method: str, path: str, body: dict | None = None):
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = urllib.request.Request(
            self.base + path,
            data=data,
            method=method,
            headers={
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                raw = resp.read().decode("utf-8")
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as e:
            err = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {path} -> {e.code}: {err[:500]}") from e

    def get(self, path: str):
        return self._req("GET", path)

    def post(self, path: str, body: dict):
        return self._req("POST", path, body)

    def put(self, path: str, body: dict):
        return self._req("PUT", path, body)


def login(base: str, user: str, password: str) -> str:
    req = urllib.request.Request(
        base.rstrip("/") + "/auth/login",
        data=json.dumps({"username": user, "password": password}).encode("utf-8"),
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))["token"]


def parse_status(text: str) -> str:
    m = re.search(r"\*\*Status:\*\*\s*([^\n*]+)", text, re.I)
    if not m:
        return "proposed"
    raw = m.group(1).strip().lower()
    # take first token-ish phrase
    for key, val in STATUS_MAP.items():
        if raw.startswith(key) or key in raw.split()[0:3]:
            return val
    if "active" in raw:
        return "accepted"
    if "propos" in raw or "draft" in raw or "open" in raw:
        return "proposed"
    if "deprecat" in raw or "supersed" in raw:
        return "deprecated"
    return "proposed"


def parse_title(text: str, reference: str) -> str:
    m = re.search(r"^#\s+(.+)$", text, re.M)
    if not m:
        return reference
    title = m.group(1).strip()
    # strip leading "ADR-037 —" / "PDR-007 —"
    title = re.sub(rf"^{re.escape(reference)}\s*[—\-:]\s*", "", title, flags=re.I)
    title = re.sub(rf"^{re.escape(reference)}\s+", "", title, flags=re.I)
    return title.strip() or reference


def split_sections(text: str) -> dict[str, str]:
    """Split markdown on ## headings into named buckets."""
    parts = re.split(r"(?m)^(##\s+.+)$", text)
    # parts[0] = preamble, then heading, body, heading, body...
    sections: dict[str, str] = {"_preamble": parts[0].strip()}
    i = 1
    while i + 1 < len(parts):
        heading = re.sub(r"^##\s+", "", parts[i]).strip().lower()
        body = parts[i + 1].strip()
        sections[heading] = body
        i += 2
    return sections


def pick_section(sections: dict[str, str], *needles: str) -> str:
    for key, body in sections.items():
        if key == "_preamble":
            continue
        for n in needles:
            if n in key:
                return body
    return ""


def classify_extra_heading(heading: str) -> str:
    """Route leftover H2s: changelog | notes | consequences."""
    h = re.sub(r"^\d+\.\s*", "", heading.lower().strip())
    if "change log" in h or "changelog" in h or "change-log" in h:
        return "changelog"
    if any(
        x in h
        for x in (
            "consequence",
            "positive",
            "trade-off",
            "tradeoff",
            "risk",
            "impact",
            "implication",
            "outcome",
        )
    ):
        return "consequences"
    # everything else that is not core Context/Decision belongs in notes
    return "notes"


def parse_md(path: Path, reference: str, category: str) -> dict:
    text = path.read_text(encoding="utf-8")
    sections = split_sections(text)
    context = pick_section(sections, "context")
    decision = pick_section(sections, "decision")
    consequences = pick_section(sections, "consequence")
    changelog = pick_section(sections, "change log", "changelog")
    notes_parts: list[str] = []

    core = {"context", "decision", "consequence", "change log", "changelog"}
    for key, body in sections.items():
        if key == "_preamble" or not body:
            continue
        if any(c in key for c in core):
            continue
        # Offline LAN marker — local-only; never import into PKS notes
        if "not yet updated in pks" in key:
            continue
        # Related → notes (metadata), not consequences
        kind = classify_extra_heading(key)
        block = f"## {key.title()}\n\n{body}".strip()
        if kind == "changelog":
            changelog = (changelog + "\n\n" + body).strip() if changelog else body
        elif kind == "consequences":
            consequences = (consequences + "\n\n" + block).strip() if consequences else block
        else:
            notes_parts.append(block)

    notes = "\n\n".join(notes_parts).strip()

    if not context:
        # heuristic: preamble without title line
        pre = sections.get("_preamble", "")
        pre = re.sub(r"^#\s+.+$", "", pre, count=1, flags=re.M).strip()
        context = pre[:4000] or f"[Imported from {path.name} — no Context section]"

    if not decision:
        decision = (
            pick_section(sections, "proposal", "locked", "summary")
            or f"[See full document {path.name} — no Decision section extracted]"
        )

    if not consequences:
        consequences = "[No Consequences section extracted]"

    # Ensure required non-empty
    context = context.strip() or "(empty context)"
    decision = decision.strip() or "(empty decision)"
    consequences = consequences.strip() or "(empty consequences)"

    status = parse_status(text)
    name = parse_title(text, reference)

    try:
        rel = str(path.resolve().relative_to(REPO))
    except Exception:
        rel = str(path)

    # Do not tag with adr/pdr/code — redundant with category + reference
    return {
        "name": name,
        "context": context,
        "decision": decision,
        "consequences": consequences,
        "changelog": changelog.strip() if changelog else None,
        "notes": notes or None,
        "category": category,
        "status": status,
        "reference": reference,
        "project_id": None,  # filled by caller
        "_tags": [],
        "_path": rel,
    }


def list_source_files(kind: str) -> list[tuple[str, Path, str]]:
    """Return list of (reference, path, category)."""
    out: list[tuple[str, Path, str]] = []
    if kind in ("pdr", "all"):
        for p in sorted(PDR_DIR.glob("PDR-*.md")):
            m = re.match(r"(PDR-\d+)", p.name, re.I)
            if not m:
                continue
            ref = m.group(1).upper().replace("PDR-", "PDR-")
            # normalize PDR-7 -> PDR-007 if needed
            num = int(re.search(r"\d+", ref).group())
            ref = f"PDR-{num:03d}"
            out.append((ref, p, "PDR"))
    if kind in ("adr", "all"):
        adr_030_paths = sorted(ADR_DIR.glob("ADR-030*.md"))
        chosen_030: Path | None = None
        for preferred in (ADR_030_PREFERRED, ADR_030_LEGACY):
            cand = ADR_DIR / preferred
            if cand.is_file():
                chosen_030 = cand
                break
        if chosen_030 is None and adr_030_paths:
            chosen_030 = adr_030_paths[0]

        for p in sorted(ADR_DIR.glob("ADR-*.md")):
            if p.name.startswith("ADR-030"):
                if chosen_030 is None or p.resolve() != chosen_030.resolve():
                    continue  # one ADR-030 only
            m = re.match(r"(ADR-\d+)", p.name, re.I)
            if not m:
                continue
            num = int(re.search(r"\d+", m.group(1)).group())
            ref = f"ADR-{num:03d}"
            out.append((ref, p, "ADR"))
    return out


PENDING_PKS_HEADING = re.compile(r"^##\s+NOT YET UPDATED in PKS\s*$", re.I | re.M)


def list_pending_pks_updates(kind: str = "all") -> list[tuple[str, Path]]:
    """Local ADR/PDR files that still carry the offline sync marker section."""
    pending: list[tuple[str, Path]] = []
    for ref, path, _cat in list_source_files(kind):
        text = path.read_text(encoding="utf-8")
        if PENDING_PKS_HEADING.search(text):
            pending.append((ref, path))
    return pending


def strip_pending_pks_section(text: str) -> str:
    """Remove the offline marker section from markdown (keep the rest)."""
    parts = re.split(r"(?=^##\s)", text, flags=re.M)
    kept: list[str] = []
    for part in parts:
        if re.match(r"^##\s+NOT YET UPDATED in PKS\s*$", part, re.I | re.M):
            continue
        # also drop when heading is first line of chunk
        if re.match(r"^##\s+NOT YET UPDATED in PKS\b", part, re.I):
            continue
        kept.append(part)
    out = "".join(kept)
    return re.sub(r"\n{3,}", "\n\n", out).rstrip() + ("\n" if out.strip() else "")


def index_existing(api: Api, project_id: int) -> dict[str, dict]:
    data = api.get("/table/xdr?limit=1000")
    rows = data.get("rows") or []
    idx = {}
    for r in rows:
        if r.get("project_id") != project_id:
            continue
        ref = r.get("reference")
        if ref:
            idx[ref] = r
    return idx


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--base-url", default=os.environ.get("INTELLI_BASE_URL", "http://olympus.local:8000"))
    ap.add_argument("--user", default=os.environ.get("INTELLI_USER", "asterix"))
    ap.add_argument("--password", default=os.environ.get("INTELLI_PASSWORD", ""))
    ap.add_argument("--project-id", type=int, default=int(os.environ.get("INTELLI_PROJECT_ID", "4")))
    ap.add_argument("--only", choices=["all", "pdr", "adr"], default="all")
    ap.add_argument(
        "--reference",
        action="append",
        default=[],
        metavar="REF",
        help="Limit to one or more references (e.g. ADR-033). Repeatable. Implies matching category.",
    )
    ap.add_argument(
        "--pending",
        action="store_true",
        help="Only files with a '## NOT YET UPDATED in PKS' section (offline queue).",
    )
    ap.add_argument(
        "--list-pending",
        action="store_true",
        help="Print offline-queue files and exit (no login required).",
    )
    ap.add_argument(
        "--clear-pending",
        action="store_true",
        help="After a successful --apply, strip '## NOT YET UPDATED in PKS' from those local files.",
    )
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--dry-run", action="store_true", default=True, help="default: do not write")
    g.add_argument("--apply", action="store_true", help="POST/PUT to intelli.db")
    args = ap.parse_args()
    dry = not args.apply

    if args.list_pending:
        pending = list_pending_pks_updates(args.only)
        print(f"pending PKS updates: {len(pending)}")
        for ref, path in pending:
            print(f"  {ref}\t{path}")
        return 0

    password = args.password
    if not password:
        print("ERROR: set INTELLI_PASSWORD or pass --password", file=sys.stderr)
        return 2

    token = login(args.base_url, args.user, password)
    api = Api(args.base_url, token)
    existing = index_existing(api, args.project_id)
    print(f"Existing steakOS XDRs with reference: {len(existing)}")

    # Order locked: PDRs then ADRs when --only all
    files = list_source_files(args.only)
    if args.only == "all":
        files = list_source_files("pdr") + list_source_files("adr")

    if args.pending:
        pend = {ref for ref, _ in list_pending_pks_updates("all")}
        files = [(ref, path, cat) for ref, path, cat in files if ref in pend]
        if not files:
            print("No local files with '## NOT YET UPDATED in PKS'")
            return 0

    if args.reference:
        want = {r.strip().upper() for r in args.reference if r.strip()}
        # normalize ADR-33 -> ADR-033, PDR-7 -> PDR-007
        normalized = set()
        for r in want:
            m = re.match(r"(ADR|PDR)-(\d+)$", r, re.I)
            if m:
                normalized.add(f"{m.group(1).upper()}-{int(m.group(2)):03d}")
            else:
                normalized.add(r)
        files = [(ref, path, cat) for ref, path, cat in files if ref in normalized]
        missing = normalized - {ref for ref, _, _ in files}
        if missing:
            print(f"WARNING: no local markdown for: {', '.join(sorted(missing))}", file=sys.stderr)
        if not files:
            print("ERROR: --reference matched no local files", file=sys.stderr)
            return 2

    report = {"create": [], "update": [], "skip": [], "error": [], "cleared": []}
    synced_paths: list[Path] = []
    for ref, path, category in files:
        try:
            payload = parse_md(path, ref, category)
            payload["project_id"] = args.project_id
            tags = payload.pop("_tags")
            src = payload.pop("_path")
            row = {
                "data": {k: v for k, v in payload.items()},
                "tags": tags,
            }
            if ref in existing:
                rid = existing[ref]["id"]
                if dry:
                    report["update"].append(f"{ref} id={rid} <- {src}")
                else:
                    api.put(f"/table/xdr/{rid}", row)
                    report["update"].append(f"{ref} id={rid} <- {src}")
                    synced_paths.append(path)
            else:
                if dry:
                    report["create"].append(f"{ref} <- {src}")
                else:
                    created = api.post("/table/xdr", row)
                    cid = created.get("id") or (created.get("data") or {}).get("id")
                    report["create"].append(f"{ref} id={cid} <- {src}")
                    synced_paths.append(path)
        except Exception as e:
            report["error"].append(f"{ref} {path.name}: {e}")

    if args.clear_pending and not dry:
        for path in synced_paths:
            text = path.read_text(encoding="utf-8")
            if not PENDING_PKS_HEADING.search(text):
                continue
            path.write_text(strip_pending_pks_section(text), encoding="utf-8")
            report["cleared"].append(str(path))

    mode = "DRY-RUN" if dry else "APPLIED"
    print(f"\n=== {mode} report ===")
    for k in ("create", "update", "skip", "error", "cleared"):
        print(f"{k}: {len(report[k])}")
        for line in report[k][:20]:
            print(f"  {line}")
        if len(report[k]) > 20:
            print(f"  ... +{len(report[k]) - 20} more")
    return 1 if report["error"] else 0


if __name__ == "__main__":
    sys.exit(main())
