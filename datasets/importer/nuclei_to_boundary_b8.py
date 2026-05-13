#!/usr/bin/env python3
"""
PR B8: Nuclei templates → Veriguard WebAttackPayload JSON converter.

Reads upstream nuclei-templates repository (https://github.com/projectdiscovery/
nuclei-templates), filters by ``info.tags`` to the 7 attack categories that
complement PR B6's 5-category set (CSRF / SSTI / XXE / BRUTE / OVERSIZED), and
emits one curated JSON file per category under ``datasets/importer/boundary_b8/``.

The 7 categories close §3.5 招标条款的 12 类边界 WAF/IPS 攻击效用矩阵：

  | category       | §3.5 中文名称       | nuclei tag    |
  | -------------- | ------------------- | ------------- |
  | sqli           | SQL 注入            | sqli          |
  | xss            | XSS                 | xss           |
  | ssrf           | SSRF                | ssrf          |
  | rce            | 命令执行            | rce           |
  | lfi            | 目录遍历            | lfi           |
  | file_upload    | 畸形表单上传绕过    | file-upload   |
  | default_login  | 弱口令登录          | default-login |

Usage:
    python3 nuclei_to_boundary_b8.py \\
        --nuclei-root /path/to/nuclei-templates \\
        --output-dir datasets/importer/boundary_b8 \\
        [--per-category 140]

Provenance:
  - Each output entry carries ``payload_external_id`` of form ``B8-<CAT>-NNN``,
    where ``<CAT>`` is one of SQLI / XSS / SSRF / RCE / LFI / UPLOAD / WEAKPWD.
  - ``payload_tags`` includes ``["b8", "<category>", "nuclei", <severity>]`` so
    operators can trace each payload back to a single Nuclei YAML.
  - The original Nuclei ``id`` is preserved in ``payload_description`` (head line)
    plus ``payload_collector`` (uses Nuclei ``info.author``).

Coverage caveats:
  - We extract only the **last** HTTP block (multi-step flows treat the last
    request as the attack step). Setup steps (auth/CSRF token fetches) are
    dropped on purpose — they fail open at runtime under a Veriguard
    ``http_inject`` since the runtime engine handles 401/403 as a covered
    "PREVENTION" outcome.
  - ``{{BaseURL}}`` and ``{{Hostname}}`` are stripped because the Veriguard
    runtime injects target host. Other Nuclei placeholders (``{{interactsh-url}}``,
    ``{{rand_int}}``, etc.) are preserved as-is so a future runtime engine can
    substitute them.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml

# ---------- 配置 ----------

# (category-key, output-filename-stem, external-id-prefix, tag-keyword,
#  optional sub-directory filter (None = any path under http/), per-category target).
@dataclass(frozen=True)
class CategoryConfig:
    key: str
    file_stem: str
    ext_prefix: str
    tag_keyword: str
    dir_filter: str | None
    target: int


CATEGORIES: tuple[CategoryConfig, ...] = (
    CategoryConfig("sqli", "sqli", "SQLI", "sqli", None, 140),
    CategoryConfig("xss", "xss", "XSS", "xss", None, 140),
    CategoryConfig("ssrf", "ssrf", "SSRF", "ssrf", None, 140),
    CategoryConfig("rce", "rce", "RCE", "rce", None, 140),
    CategoryConfig("lfi", "lfi", "LFI", "lfi", None, 140),
    CategoryConfig("file_upload", "file_upload", "UPLOAD", "file-upload", None, 140),
    CategoryConfig("default_login", "default_login", "WEAKPWD", "default-login", "default-logins", 140),
)

TO_CLASSIFY_DOMAIN = {
    "domain_id": "8144f32d-c2cf-43e0-a999-358628027817",
    "domain_name": "To classify",
    "domain_color": "#FFFFFF",
}

# Nuclei placeholders we must scrub from URL path (host-equivalent).
HOST_PLACEHOLDERS = ("{{BaseURL}}", "{{Hostname}}", "{{RootURL}}", "{{Host}}")

# ---------- 工具函数 ----------


def load_yaml(path: Path) -> dict[str, Any] | None:
    """Robust YAML load that tolerates Nuclei's custom directives."""
    try:
        with path.open("r", encoding="utf-8", errors="replace") as fh:
            return yaml.safe_load(fh)
    except yaml.YAMLError:
        return None


def get_tags(doc: dict[str, Any]) -> list[str]:
    """Nuclei tag field is a comma-separated string under info.tags."""
    info = doc.get("info") or {}
    raw = info.get("tags") or ""
    if isinstance(raw, list):
        return [str(t).strip() for t in raw]
    return [t.strip() for t in str(raw).split(",") if t.strip()]


def get_severity(doc: dict[str, Any]) -> str:
    return str((doc.get("info") or {}).get("severity") or "unknown").lower()


def resolve_variables(text: str, variables: dict[str, str]) -> str:
    """Substitute simple {{var}} references from the variables: block."""
    for k, v in variables.items():
        text = text.replace("{{" + k + "}}", str(v))
    return text


def strip_host_placeholders(url: str) -> str:
    for ph in HOST_PLACEHOLDERS:
        url = url.replace(ph, "")
    if not url.startswith("/"):
        url = "/" + url
    # collapse leading double slashes that result from `{{BaseURL}}/foo`
    while url.startswith("//"):
        url = url[1:]
    return url


def parse_raw_request(raw: str) -> dict[str, Any] | None:
    """Parse a Nuclei ``raw:`` HTTP request blob into structured fields."""
    raw = raw.lstrip()
    # Strip leading nuclei directives like @timeout 20s / @tls-sni / @Host:
    while raw.startswith("@"):
        eol = raw.find("\n")
        if eol < 0:
            return None
        raw = raw[eol + 1 :]
    lines = raw.split("\n")
    if not lines:
        return None
    request_line = lines[0].strip()
    parts = request_line.split(" ", 2)
    if len(parts) < 2:
        return None
    method = parts[0].strip().upper()
    path = parts[1].strip()

    headers: list[dict[str, str]] = []
    body_idx = -1
    for i, line in enumerate(lines[1:], start=1):
        if line == "":
            body_idx = i + 1
            break
        if ":" in line:
            name, _, value = line.partition(":")
            name = name.strip()
            value = value.strip()
            if name.lower() == "host":
                continue  # Veriguard runtime injects host
            if name and value:
                headers.append({"name": name, "value": value})
    body = ""
    if body_idx > 0 and body_idx < len(lines):
        body = "\n".join(lines[body_idx:]).rstrip("\n")
    return {
        "method": method,
        "path": path,
        "headers": headers,
        "body": body,
    }


def infer_body_type(headers: list[dict[str, str]], body: str) -> str:
    for h in headers:
        if h["name"].lower() == "content-type":
            ct = h["value"].lower()
            if "json" in ct:
                return "json"
            if "x-www-form-urlencoded" in ct:
                return "form"
            if "multipart" in ct:
                return "multipart"
            if "xml" in ct:
                return "xml"
    if not body:
        return "text"
    stripped = body.strip()
    if stripped.startswith("{") or stripped.startswith("["):
        return "json"
    if "=" in stripped and "&" in stripped:
        return "form"
    return "text"


DSL_STATUS_RE = re.compile(r"status_code\s*==\s*(\d+)")
DSL_STATUS_IN_RE = re.compile(r"status_code\s+in\s+\(([^)]+)\)")


def extract_expected_status(http_block: dict[str, Any]) -> list[int]:
    codes: list[int] = []
    matchers = http_block.get("matchers") or []
    for m in matchers:
        if not isinstance(m, dict):
            continue
        if m.get("type") == "status":
            for code in m.get("status") or []:
                try:
                    codes.append(int(code))
                except (TypeError, ValueError):
                    pass
        elif m.get("type") == "dsl":
            for expr in m.get("dsl") or []:
                if not isinstance(expr, str):
                    continue
                for hit in DSL_STATUS_RE.findall(expr):
                    codes.append(int(hit))
                for grp in DSL_STATUS_IN_RE.findall(expr):
                    for piece in grp.split(","):
                        piece = piece.strip()
                        if piece.isdigit():
                            codes.append(int(piece))
    # de-dupe while preserving order
    seen: set[int] = set()
    deduped: list[int] = []
    for c in codes:
        if c not in seen:
            seen.add(c)
            deduped.append(c)
    return deduped


def extract_expected_regex(http_block: dict[str, Any]) -> str:
    fragments: list[str] = []
    matchers = http_block.get("matchers") or []
    for m in matchers:
        if not isinstance(m, dict):
            continue
        if m.get("part") and m["part"] != "body":
            continue
        mt = m.get("type")
        if mt == "word":
            for w in m.get("words") or []:
                fragments.append(re.escape(str(w)))
        elif mt == "regex":
            for r in m.get("regex") or []:
                fragments.append(str(r))
    if not fragments:
        return ""
    joined = "|".join(fragments)
    # Cap the regex at 800 chars to keep DB rows bounded.
    if len(joined) > 800:
        joined = joined[:800]
    return joined


def pick_attack_block(http_blocks: list[dict[str, Any]]) -> dict[str, Any] | None:
    """The last http block is typically the actual attack step."""
    for b in reversed(http_blocks):
        if isinstance(b, dict):
            return b
    return None


def extract_request_fields(doc: dict[str, Any]) -> dict[str, Any] | None:
    """Return method/url/body/body_type/headers/expected_* for the attack step."""
    http_blocks = doc.get("http") or []
    if not isinstance(http_blocks, list) or not http_blocks:
        return None
    block = pick_attack_block(http_blocks)
    if block is None:
        return None
    variables = {
        k: str(v) for k, v in (doc.get("variables") or {}).items() if isinstance(k, str)
    }

    method = None
    path = None
    headers: list[dict[str, str]] = []
    body = ""

    if "raw" in block and block["raw"]:
        raw_list = block["raw"]
        if not isinstance(raw_list, list) or not raw_list:
            return None
        parsed = parse_raw_request(str(raw_list[0]))
        if parsed is None:
            return None
        method = parsed["method"]
        path = parsed["path"]
        headers = parsed["headers"]
        body = parsed["body"]
    else:
        method = str(block.get("method") or "GET").upper()
        paths = block.get("path") or []
        if isinstance(paths, list) and paths:
            path = str(paths[0])
        elif isinstance(paths, str):
            path = paths
        else:
            return None
        raw_headers = block.get("headers") or {}
        if isinstance(raw_headers, dict):
            for k, v in raw_headers.items():
                if str(k).lower() == "host":
                    continue
                headers.append({"name": str(k), "value": str(v)})
        body_val = block.get("body")
        if body_val is not None:
            body = str(body_val)

    # Resolve variables in path/body/header values
    path = resolve_variables(path or "", variables)
    body = resolve_variables(body or "", variables)
    headers = [
        {"name": h["name"], "value": resolve_variables(h["value"], variables)}
        for h in headers
    ]

    url = strip_host_placeholders(path)
    body_type = infer_body_type(headers, body)
    expected_codes = extract_expected_status(block)
    expected_regex = extract_expected_regex(block)
    return {
        "method": method,
        "url": url,
        "body": body,
        "body_type": body_type,
        "headers": headers,
        "expected_status_codes": expected_codes,
        "expected_body_regex": expected_regex,
    }


def classify(doc: dict[str, Any], rel_path: Path) -> str | None:
    """Pick the most specific matching category for this template."""
    tags = {t.lower() for t in get_tags(doc)}
    rel_str = str(rel_path).lower()
    # 1) directory-based hard filter for default_login (more reliable than tags)
    if "default-logins/" in rel_str:
        return "default_login"
    # 2) tag-based; check in order of priority (file-upload before rce so upload
    #    CVEs that also tag rce get classified under upload)
    priority = ["default-login", "file-upload", "ssrf", "lfi", "sqli", "rce", "xss"]
    cat_by_tag = {
        "default-login": "default_login",
        "file-upload": "file_upload",
        "ssrf": "ssrf",
        "lfi": "lfi",
        "sqli": "sqli",
        "rce": "rce",
        "xss": "xss",
    }
    for tag in priority:
        if tag in tags:
            return cat_by_tag[tag]
    return None


def truncate(s: str, n: int) -> str:
    if s is None:
        return ""
    s = str(s)
    if len(s) <= n:
        return s
    return s[: n - 3] + "..."


def scrub_nul(value: Any) -> Any:
    """Recursively replace literal NUL bytes (U+0000) with the percent-encoded form ``%00``.

    PostgreSQL TEXT/JSONB rejects NUL bytes; see PR #45 +踩坑 #2 in
    [[project-ipv6-dev-env-workflow]]. Nuclei XXE/binary templates routinely
    embed ``\\u0000`` in attack bodies (e.g. CVE-2017-5983 JIRA jwd amf), which
    Jackson surfaces to JPA as a real ``0x00`` byte and crashes the batch insert.
    """
    if isinstance(value, str):
        return value.replace("\x00", "%00")
    if isinstance(value, list):
        return [scrub_nul(v) for v in value]
    if isinstance(value, dict):
        return {k: scrub_nul(v) for k, v in value.items()}
    return value


def build_payload_entry(
    doc: dict[str, Any],
    rel_path: Path,
    fields: dict[str, Any],
    category: CategoryConfig,
    index: int,
) -> dict[str, Any]:
    info = doc.get("info") or {}
    name = truncate(info.get("name") or rel_path.stem, 200)
    desc_head = truncate(
        (info.get("description") or "").strip().split("\n")[0] or name,
        300,
    )
    severity = get_severity(doc)
    tags_input = get_tags(doc)
    tag_set = {"b8", category.key, "nuclei"}
    if severity != "unknown":
        tag_set.add(f"sev-{severity}")
    # `tags_input` from upstream Nuclei is intentionally NOT surfaced here:
    # noisy upstream tags (vendor/product/cve year) would explode the DB tag table.

    external_id = f"B8-{category.ext_prefix}-{index:03d}"
    author = truncate(str(info.get("author") or "nuclei"), 80)
    entry = {
        "payload_type": "WebAttack",
        "payload_name": f"{name} (Nuclei: {doc.get('id') or rel_path.stem})",
        "payload_external_id": external_id,
        "payload_source": "COMMUNITY",
        "payload_status": "VERIFIED",
        # payload_collector is a Veriguard FK (Collector UUID), not free text.
        # Author is preserved inside payload_description for provenance.
        "payload_description": (
            f"[Nuclei {doc.get('id') or rel_path.stem} by {author}] {desc_head}"
        ),
        "payload_platforms": ["Generic"],
        "payload_execution_arch": "ALL_ARCHITECTURES",
        "payload_expectations": ["PREVENTION", "DETECTION"],
        "payload_attack_patterns": [],
        "payload_domains": [TO_CLASSIFY_DOMAIN],
        "payload_tags": sorted(tag_set),
        "web_request_method": fields["method"],
        "web_request_url": fields["url"],
        "web_request_headers": fields["headers"],
        "web_request_body": fields["body"],
        "web_request_body_type": fields["body_type"],
        "expected_status_codes": fields["expected_status_codes"],
        "expected_body_regex": fields["expected_body_regex"],
    }
    # PG TEXT/JSONB rejects 0x00; scrub recursively across every string field.
    return scrub_nul(entry)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build PR B8 boundary JSON from Nuclei templates")
    parser.add_argument("--nuclei-root", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument(
        "--per-category", type=int, default=140,
        help="Cap per category (default 140 → ~980 total, slightly above §3.5 ≥500)",
    )
    args = parser.parse_args()

    nuclei_root: Path = args.nuclei_root
    out_dir: Path = args.output_dir
    if not nuclei_root.is_dir():
        print(f"ERROR: --nuclei-root not a directory: {nuclei_root}", file=sys.stderr)
        return 2
    http_root = nuclei_root / "http"
    if not http_root.is_dir():
        print(f"ERROR: missing http/ under {nuclei_root}", file=sys.stderr)
        return 2

    # 收集候选 (sorted by relative path for reproducibility)
    yamls = sorted(http_root.rglob("*.yaml"))
    print(f"scanning {len(yamls)} YAMLs under {http_root} ...")

    buckets: dict[str, list[tuple[Path, dict[str, Any]]]] = defaultdict(list)
    parse_failures = 0
    classify_failures = 0
    extract_failures = 0

    for path in yamls:
        rel = path.relative_to(nuclei_root)
        doc = load_yaml(path)
        if not isinstance(doc, dict):
            parse_failures += 1
            continue
        cat_key = classify(doc, rel)
        if cat_key is None:
            classify_failures += 1
            continue
        # Override per category target — apply cap during emission, not collection
        buckets[cat_key].append((rel, doc))

    # 第二遍：解 HTTP + 限 cap
    out_dir.mkdir(parents=True, exist_ok=True)
    summary: list[tuple[str, int]] = []
    for cat in CATEGORIES:
        rows = buckets.get(cat.key, [])
        rows.sort(key=lambda t: str(t[0]))
        entries: list[dict[str, Any]] = []
        for rel, doc in rows:
            if len(entries) >= cat.target:
                break
            fields = extract_request_fields(doc)
            if fields is None or not fields["method"] or not fields["url"]:
                extract_failures += 1
                continue
            entries.append(
                build_payload_entry(doc, rel, fields, cat, len(entries) + 1)
            )
        out_path = out_dir / f"{cat.file_stem}.json"
        with out_path.open("w", encoding="utf-8") as fh:
            json.dump(entries, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        summary.append((cat.key, len(entries)))
        print(f"  → {out_path.name}  {len(entries)} entries")

    total = sum(n for _, n in summary)
    print(
        f"\n=== summary ===\n  parse_failures: {parse_failures}"
        f"\n  classify_failures: {classify_failures}"
        f"\n  extract_failures: {extract_failures}"
        f"\n  total B8 entries: {total}"
    )
    for k, n in summary:
        print(f"    {k}: {n}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
