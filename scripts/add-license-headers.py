#!/usr/bin/env python3
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

"""Add Apache-2.0 license headers to first-party source files (idempotent).

Usage (from repo root):
  python3 scripts/add-license-headers.py
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SCAN = [
    ROOT / "grails-app",
    ROOT / "src",
    ROOT / "frontend" / "src",
    ROOT / "scripts",
]
EXTRA = [
    ROOT / "build.gradle",
    ROOT / "settings.gradle",
    ROOT / "frontend" / "vite.config.js",
    ROOT / "frontend" / "vite.config.ts",
    ROOT / "frontend" / "eslint.config.js",
    ROOT / "grails-app" / "conf" / "application.yml",
    ROOT / "grails-app" / "conf" / "logback-spring.xml",
]

SKIP_PARTS = {"node_modules", "build", ".gradle", "dist", "target"}
SKIP_SUFFIX = {
    ".json",
    ".svg",
    ".png",
    ".jpg",
    ".jpeg",
    ".gif",
    ".ico",
    ".woff",
    ".woff2",
    ".map",
    ".lock",
}

LINE = {
    ".groovy",
    ".java",
    ".kt",
    ".kts",
    ".gradle",
    ".ts",
    ".tsx",
    ".js",
    ".jsx",
    ".mjs",
    ".cjs",
    ".sh",
    ".bash",
    ".py",
    ".yml",
    ".yaml",
}
BLOCK = {".css", ".scss"}
XML = {".xml"}

SPDX = "SPDX-License-Identifier: Apache-2.0"
COPY = "Copyright 2026 The steakOS Authors"
BODY = (
    "Licensed under the Apache License, Version 2.0 (the \"License\");\n"
    "you may not use this file except in compliance with the License.\n"
    "You may obtain a copy of the License at\n"
    "\n"
    "    http://www.apache.org/licenses/LICENSE-2.0\n"
    "\n"
    "Unless required by applicable law or agreed to in writing, software\n"
    "distributed under the License is distributed on an \"AS IS\" BASIS,\n"
    "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
    "See the License for the specific language governing permissions and\n"
    "limitations under the License."
)


def header_for(path: Path) -> str:
    suf = path.suffix.lower()
    if suf in BLOCK:
        lines = [SPDX, COPY, ""] + BODY.split("\n")
        return "/*\n" + "\n".join(f" * {l}" if l else " *" for l in lines) + "\n */\n\n"
    if suf in XML:
        lines = [SPDX, COPY, ""] + BODY.split("\n")
        return "<!--\n" + "\n".join(f"  {l}" if l else "" for l in lines) + "\n-->\n"
    if suf in {".sh", ".bash", ".py", ".yml", ".yaml"} or path.name.endswith(".sh"):
        c = "#"
    else:
        c = "//"
    lines = [f"{c} {SPDX}", f"{c} {COPY}", c]
    for line in BODY.split("\n"):
        lines.append(f"{c} {line}" if line else c)
    return "\n".join(lines) + "\n\n"


def has_license(text: str) -> bool:
    head = text[:1200]
    return "SPDX-License-Identifier:" in head or "Licensed under the Apache License" in head


def should_process(path: Path) -> bool:
    if any(p in SKIP_PARTS for p in path.parts):
        return False
    if path.suffix.lower() in SKIP_SUFFIX:
        return False
    if path.name.endswith(".min.js"):
        return False
    suf = path.suffix.lower()
    if suf in LINE or suf in BLOCK or suf in XML:
        return True
    if path.name.endswith(".gradle"):
        return True
    return False


def process(path: Path) -> str:
    if not should_process(path):
        return "skip"
    try:
        raw = path.read_text(encoding="utf-8")
    except OSError:
        return "error"
    if not raw.strip():
        return "skip-empty"
    if has_license(raw):
        return "exists"
    header = header_for(path)
    if raw.startswith("#!"):
        first, _, rest = raw.partition("\n")
        new = first + "\n" + header + rest
    elif raw.startswith("<?xml"):
        first, _, rest = raw.partition("\n")
        new = first + "\n" + header + rest
    else:
        new = header + raw
    path.write_text(new, encoding="utf-8")
    return "added"


def main() -> None:
    candidates: list[Path] = []
    for base in SCAN:
        if base.exists():
            candidates.extend(p for p in base.rglob("*") if p.is_file())
    for p in EXTRA:
        if p.exists():
            candidates.append(p)
    stats: dict[str, int] = {}
    for p in sorted(set(candidates)):
        st = process(p)
        stats[st] = stats.get(st, 0) + 1
    print("license header pass:")
    for k, v in sorted(stats.items()):
        print(f"  {k}: {v}")


if __name__ == "__main__":
    main()
