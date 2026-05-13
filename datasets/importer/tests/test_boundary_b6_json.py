#!/usr/bin/env python3
"""
PR B6 数据集 JSON 校验测试。

可独立 main 运行：
    python3 datasets/importer/tests/test_boundary_b6_json.py

也可被 pytest 收集（test_* 函数被 unittest.main 兼容）。

校验目标：
  - 5 个 JSON 文件存在
  - 每文件 ≥ 30 条 payload
  - 每条必备字段齐全：
        payload_type == "WebAttack"
        payload_external_id (唯一)
        payload_name
        payload_description
        web_request_method
        web_request_url
        web_request_body_type
  - payload_external_id 跨 5 文件全局唯一
  - 不含 NUL 字节（PG JSONB 兼容）
  - payload_domains 至少 1 个
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
B6_DIR = REPO_ROOT / "importer" / "boundary_b6"

EXPECTED_FILES = [
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

MIN_PER_FILE = 30


def _load(name: str) -> list[dict]:
    path = B6_DIR / name
    assert path.exists(), f"missing {path}"
    raw = path.read_text(encoding="utf-8")
    assert "\x00" not in raw, f"{name} contains NUL byte (PG JSONB incompatible)"
    items = json.loads(raw)
    assert isinstance(items, list), f"{name} must be a JSON array"
    return items


def test_files_exist():
    for name in EXPECTED_FILES:
        assert (B6_DIR / name).exists(), f"missing dataset file {name}"


def test_min_count_per_file():
    for name in EXPECTED_FILES:
        items = _load(name)
        assert len(items) >= MIN_PER_FILE, f"{name} has {len(items)} < {MIN_PER_FILE}"


def test_required_fields_present():
    for name in EXPECTED_FILES:
        items = _load(name)
        for idx, item in enumerate(items):
            for field in REQUIRED_FIELDS:
                assert field in item and item[field] not in (
                    None,
                    "",
                    [],
                ), f"{name}[{idx}] missing required field {field}"
            assert item["payload_type"] == "WebAttack", (
                f"{name}[{idx}] payload_type must be 'WebAttack', got {item['payload_type']}"
            )
            assert isinstance(item["payload_domains"], list) and len(item["payload_domains"]) >= 1, (
                f"{name}[{idx}] payload_domains must contain ≥1 entry"
            )
            assert isinstance(item["payload_platforms"], list) and len(item["payload_platforms"]) >= 1, (
                f"{name}[{idx}] payload_platforms must contain ≥1 entry"
            )


def test_external_ids_globally_unique():
    seen: dict[str, str] = {}
    for name in EXPECTED_FILES:
        items = _load(name)
        for idx, item in enumerate(items):
            ext_id = item["payload_external_id"]
            assert ext_id, f"{name}[{idx}] empty payload_external_id"
            assert ext_id not in seen, (
                f"duplicate payload_external_id {ext_id}: first seen in {seen[ext_id]}, again in {name}[{idx}]"
            )
            seen[ext_id] = f"{name}[{idx}]"


def test_no_nul_bytes_in_json():
    for name in EXPECTED_FILES:
        path = B6_DIR / name
        raw = path.read_bytes()
        assert b"\x00" not in raw, f"{name} contains NUL byte"


def main() -> int:
    tests = [
        ("files_exist", test_files_exist),
        ("min_count_per_file", test_min_count_per_file),
        ("required_fields_present", test_required_fields_present),
        ("external_ids_globally_unique", test_external_ids_globally_unique),
        ("no_nul_bytes_in_json", test_no_nul_bytes_in_json),
    ]
    failures = []
    for name, fn in tests:
        try:
            fn()
            print(f"PASS  {name}")
        except AssertionError as exc:
            failures.append((name, str(exc)))
            print(f"FAIL  {name}: {exc}", file=sys.stderr)

    total_count = sum(len(_load(n)) for n in EXPECTED_FILES)
    print(f"\nTotal payloads across 5 files: {total_count}")
    if failures:
        print(f"\n{len(failures)} test(s) failed", file=sys.stderr)
        return 1
    print(f"\nAll {len(tests)} tests passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
