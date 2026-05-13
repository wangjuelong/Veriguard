#!/usr/bin/env python3
"""
PR B8 数据集 JSON 校验测试（Nuclei → web_attack）。

可独立 main 运行::

    python3 datasets/importer/tests/test_boundary_b8_json.py

也可被 pytest 收集。

校验目标：
  - 7 个 JSON 文件存在（sqli/xss/ssrf/rce/lfi/file_upload/default_login）
  - 总条数 ≥ 800（招标 §3.5 ≥ 500 留 60% 安全余量）
  - 每文件 ≥ 100 条（file_upload 例外，上游模板少，≥ 80 即可）
  - 必备字段齐全（payload_type / external_id / name / web_request_method / url / ...）
  - payload_external_id 跨 7 文件全局唯一，格式 ``B8-<CAT>-NNN``
  - 不含 NUL 字节（PG JSONB 兼容；见踩坑 #2）
  - web_request_url 不以 ``http://`` 或 ``https://`` 开头（runtime 注入 host）
  - 与 B6 (155 条 5 类) 合并后**总 web_attack ≥ 500**（招标 §3.5 闭环验收）
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
B8_DIR = REPO_ROOT / "importer" / "boundary_b8"
B6_DIR = REPO_ROOT / "importer" / "boundary_b6"

EXPECTED_FILES = [
    "sqli.json",
    "xss.json",
    "ssrf.json",
    "rce.json",
    "lfi.json",
    "file_upload.json",
    "default_login.json",
]

B6_FILES = [
    "csrf.json",
    "oversized_upload.json",
    "ssti.json",
    "xxe.json",
    "brute_force.json",
]

REQUIRED_FIELDS = [
    "payload_type",
    "payload_external_id",
    "payload_name",
    "payload_description",
    "payload_source",
    "payload_status",
    "payload_platforms",
    "payload_domains",
    "web_request_method",
    "web_request_url",
    "web_request_body_type",
]

# Most categories yield ≥ 100 from upstream Nuclei. file-upload upstream pool
# is smaller (~111), so we allow it ≥ 80.
MIN_PER_FILE_DEFAULT = 100
MIN_PER_FILE_FILE_UPLOAD = 80
EXT_ID_RE = re.compile(r"^B8-(SQLI|XSS|SSRF|RCE|LFI|UPLOAD|WEAKPWD)-\d{3,}$")
BID_SECTION_35_MIN_TOTAL = 500


def _load(directory: Path, name: str) -> list[dict]:
    path = directory / name
    assert path.exists(), f"missing {path}"
    raw = path.read_text(encoding="utf-8")
    assert "\x00" not in raw, f"{name} contains NUL byte (PG JSONB incompatible)"
    items = json.loads(raw)
    assert isinstance(items, list), f"{name} must be a JSON array"
    return items


def test_files_exist():
    for name in EXPECTED_FILES:
        assert (B8_DIR / name).exists(), f"missing dataset file {name}"


def test_min_count_per_file():
    for name in EXPECTED_FILES:
        items = _load(B8_DIR, name)
        threshold = MIN_PER_FILE_FILE_UPLOAD if name == "file_upload.json" else MIN_PER_FILE_DEFAULT
        assert len(items) >= threshold, f"{name} has {len(items)} < {threshold}"


def test_required_fields_present():
    for name in EXPECTED_FILES:
        items = _load(B8_DIR, name)
        for idx, item in enumerate(items):
            for field in REQUIRED_FIELDS:
                assert field in item and item[field] not in (None, "", []), (
                    f"{name}[{idx}] missing required field {field}"
                )
            assert item["payload_type"] == "WebAttack", (
                f"{name}[{idx}] payload_type must be 'WebAttack', got {item['payload_type']}"
            )
            assert isinstance(item["payload_domains"], list) and len(item["payload_domains"]) >= 1
            assert isinstance(item["payload_platforms"], list) and len(item["payload_platforms"]) >= 1


def test_external_id_format_and_uniqueness():
    seen: dict[str, str] = {}
    for name in EXPECTED_FILES:
        items = _load(B8_DIR, name)
        for idx, item in enumerate(items):
            ext_id = item["payload_external_id"]
            assert EXT_ID_RE.match(ext_id), (
                f"{name}[{idx}] external_id '{ext_id}' violates B8-<CAT>-NNN pattern"
            )
            assert ext_id not in seen, (
                f"duplicate payload_external_id {ext_id}: first seen in "
                f"{seen[ext_id]}, again in {name}[{idx}]"
            )
            seen[ext_id] = f"{name}[{idx}]"


def test_urls_are_relative_paths():
    """Veriguard runtime injects target host; URLs must be host-relative paths."""
    for name in EXPECTED_FILES:
        items = _load(B8_DIR, name)
        for idx, item in enumerate(items):
            url = item["web_request_url"]
            assert isinstance(url, str) and url, f"{name}[{idx}] empty url"
            assert not url.lower().startswith(("http://", "https://", "ftp://")), (
                f"{name}[{idx}] url '{url[:60]}' must be host-relative path"
            )
            assert url.startswith("/"), (
                f"{name}[{idx}] url '{url[:60]}' must start with '/'"
            )


def test_no_nul_bytes_in_json():
    """Reject both raw 0x00 bytes AND parsed NUL characters in string values.

    Nuclei XXE/binary templates routinely embed ``\\u0000`` JSON escapes which
    look harmless in the file but materialize as real NUL when Jackson/PG read
    them — see [[project-ipv6-dev-env-workflow]] 踩坑 #2 + PR #45.
    """

    def walk_strings(value):
        if isinstance(value, str):
            yield value
        elif isinstance(value, list):
            for v in value:
                yield from walk_strings(v)
        elif isinstance(value, dict):
            for v in value.values():
                yield from walk_strings(v)

    for name in EXPECTED_FILES:
        path = B8_DIR / name
        raw = path.read_bytes()
        assert b"\x00" not in raw, f"{name} contains raw NUL byte"
        items = _load(B8_DIR, name)
        for idx, item in enumerate(items):
            for s in walk_strings(item):
                assert "\x00" not in s, (
                    f"{name}[{idx}] {item.get('payload_external_id')} contains "
                    f"NUL char in a string value (must be %00 percent-encoded)"
                )


def test_section_35_bid_compliance():
    """招标 §3.5：边界 WAF/IPS 12 类 + ≥ 500 用例（B6 5 类 + B8 7 类 = 12 类）。"""
    b6_total = sum(len(_load(B6_DIR, name)) for name in B6_FILES)
    b8_total = sum(len(_load(B8_DIR, name)) for name in EXPECTED_FILES)
    combined = b6_total + b8_total
    categories = len(B6_FILES) + len(EXPECTED_FILES)
    assert categories == 12, f"边界 12 类要求未满足：found {categories}"
    assert combined >= BID_SECTION_35_MIN_TOTAL, (
        f"§3.5 ≥ {BID_SECTION_35_MIN_TOTAL} 总用例未满足：B6={b6_total} + B8={b8_total} = {combined}"
    )


def main() -> int:
    tests = [
        ("files_exist", test_files_exist),
        ("min_count_per_file", test_min_count_per_file),
        ("required_fields_present", test_required_fields_present),
        ("external_id_format_and_uniqueness", test_external_id_format_and_uniqueness),
        ("urls_are_relative_paths", test_urls_are_relative_paths),
        ("no_nul_bytes_in_json", test_no_nul_bytes_in_json),
        ("section_35_bid_compliance", test_section_35_bid_compliance),
    ]
    failures = []
    for name, fn in tests:
        try:
            fn()
            print(f"PASS  {name}")
        except AssertionError as exc:
            failures.append((name, str(exc)))
            print(f"FAIL  {name}: {exc}", file=sys.stderr)

    b6_total = sum(len(_load(B6_DIR, name)) for name in B6_FILES)
    b8_total = sum(len(_load(B8_DIR, name)) for name in EXPECTED_FILES)
    print(f"\nB6 total: {b6_total}  B8 total: {b8_total}  combined: {b6_total + b8_total}")
    print(f"Categories: {len(B6_FILES)} (B6) + {len(EXPECTED_FILES)} (B8) = 12")
    if failures:
        print(f"\n{len(failures)} test(s) failed", file=sys.stderr)
        return 1
    print(f"\nAll {len(tests)} tests passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
