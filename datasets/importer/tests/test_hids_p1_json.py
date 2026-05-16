#!/usr/bin/env python3
"""
P1.1 HIDS 数据集 JSON 校验测试（IPv6 安全验证系统招标 §5）.

可独立 main 运行：
    python3 datasets/importer/tests/test_hids_p1_json.py

也可被 pytest 收集（test_* 函数被 unittest.main 兼容）。

校验目标：
  - 12 个 JSON 文件存在
  - 每文件 ≥ 25 条 payload
  - 总数 ≥ 300
  - 每条必备字段齐全：
        payload_type == "HostAttack"
        payload_external_id (唯一，前缀 "HIDS-")
        payload_name / payload_description / payload_source / payload_status
        payload_platforms / payload_expectations / payload_domains 非空
        hids_category 严校到 12 类之一
        hids_execution_mode 严校到 7 种之一
        hids_command_template 非空
  - payload_external_id 跨 12 文件全局唯一
  - 不含 NUL 字节（PG JSONB 兼容）
  - payload_domains 至少 1 个
  - 12 类全覆盖（与文件名一一对应）
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
HIDS_DIR = REPO_ROOT / "importer" / "hids_p1"

EXPECTED_FILES = [
    "reverse_shell.json",
    "webshell_drop.json",
    "command_execution.json",
    "tunnel_proxy.json",
    "memory_webshell_inject.json",
    "brute_force.json",
    "rat_execution.json",
    "privilege_escalation.json",
    "website_tampering.json",
    "virus_sample_drop.json",
    "trace_cleanup.json",
    "host_persistence.json",
]

REQUIRED_FIELDS = [
    "payload_type",
    "payload_external_id",
    "payload_name",
    "payload_description",
    "payload_source",
    "payload_status",
    "payload_platforms",
    "payload_expectations",
    "payload_domains",
    "hids_category",
    "hids_execution_mode",
    "hids_command_template",
]

VALID_CATEGORIES = {
    "reverse_shell",
    "webshell_drop",
    "command_execution",
    "tunnel_proxy",
    "memory_webshell_inject",
    "brute_force",
    "rat_execution",
    "privilege_escalation",
    "website_tampering",
    "virus_sample_drop",
    "trace_cleanup",
    "host_persistence",
}

VALID_MODES = {
    "command",
    "executable",
    "file_drop",
    "memory_inject",
    "registry",
    "service",
    "scheduled_task",
}

MIN_PER_FILE = 25
MIN_TOTAL = 300


def _load(name: str) -> list[dict]:
    path = HIDS_DIR / name
    assert path.exists(), f"missing {path}"
    raw = path.read_text(encoding="utf-8")
    assert "\x00" not in raw, f"{name} contains NUL byte (PG JSONB incompatible)"
    items = json.loads(raw)
    assert isinstance(items, list), f"{name} must be a JSON array"
    return items


def test_files_exist() -> None:
    for name in EXPECTED_FILES:
        assert (HIDS_DIR / name).exists(), f"missing dataset file {name}"
    assert len(EXPECTED_FILES) == 12, "must cover 12 categories"


def test_min_count_per_file() -> None:
    for name in EXPECTED_FILES:
        items = _load(name)
        assert len(items) >= MIN_PER_FILE, f"{name} has {len(items)} < {MIN_PER_FILE}"


def test_total_count_meets_bid_requirement() -> None:
    total = sum(len(_load(n)) for n in EXPECTED_FILES)
    assert total >= MIN_TOTAL, f"total {total} < bid requirement {MIN_TOTAL}"


def test_required_fields_present() -> None:
    for name in EXPECTED_FILES:
        items = _load(name)
        for idx, item in enumerate(items):
            for field in REQUIRED_FIELDS:
                assert field in item, f"{name}[{idx}] missing field {field}"
                assert item[field] not in (None, "", []), (
                    f"{name}[{idx}] {field} is empty/None"
                )
            assert item["payload_type"] == "HostAttack", (
                f"{name}[{idx}] payload_type must be 'HostAttack', got {item['payload_type']}"
            )
            assert isinstance(item["payload_domains"], list) and len(item["payload_domains"]) >= 1, (
                f"{name}[{idx}] payload_domains must contain ≥1 entry"
            )
            assert isinstance(item["payload_platforms"], list) and len(item["payload_platforms"]) >= 1, (
                f"{name}[{idx}] payload_platforms must contain ≥1 entry"
            )


def test_external_ids_globally_unique_and_prefixed() -> None:
    seen: dict[str, str] = {}
    for name in EXPECTED_FILES:
        items = _load(name)
        for idx, item in enumerate(items):
            ext_id = item["payload_external_id"]
            assert ext_id, f"{name}[{idx}] empty payload_external_id"
            assert ext_id.startswith("HIDS-"), (
                f"{name}[{idx}] payload_external_id {ext_id!r} must start with 'HIDS-'"
            )
            assert ext_id not in seen, (
                f"duplicate payload_external_id {ext_id}: first seen in {seen[ext_id]}, again in {name}[{idx}]"
            )
            seen[ext_id] = f"{name}[{idx}]"


def test_hids_category_matches_filename() -> None:
    """每个 JSON 内所有 entry 的 hids_category 必须等于文件名（去 .json）."""
    for name in EXPECTED_FILES:
        cat_from_filename = name.removesuffix(".json")
        assert cat_from_filename in VALID_CATEGORIES, f"unknown category in filename {name}"
        items = _load(name)
        for idx, item in enumerate(items):
            assert item["hids_category"] == cat_from_filename, (
                f"{name}[{idx}] hids_category={item['hids_category']!r} ≠ filename {cat_from_filename!r}"
            )


def test_hids_execution_mode_in_allowed_set() -> None:
    for name in EXPECTED_FILES:
        items = _load(name)
        for idx, item in enumerate(items):
            mode = item["hids_execution_mode"]
            assert mode in VALID_MODES, (
                f"{name}[{idx}] hids_execution_mode={mode!r} not in {sorted(VALID_MODES)}"
            )


def test_optional_shapes() -> None:
    """非必备但若存在则需正确：attack_patterns/list、expected_artifacts/list、artifact_path 可空。"""
    for name in EXPECTED_FILES:
        items = _load(name)
        for idx, item in enumerate(items):
            ap = item.get("payload_attack_patterns")
            assert isinstance(ap, list), f"{name}[{idx}] payload_attack_patterns must be list"
            ea = item.get("hids_expected_artifacts")
            assert isinstance(ea, list), f"{name}[{idx}] hids_expected_artifacts must be list"
            ap_path = item.get("hids_artifact_path")
            assert ap_path is None or (isinstance(ap_path, str) and ap_path), (
                f"{name}[{idx}] hids_artifact_path must be null or non-empty string"
            )


def test_no_nul_bytes_in_json() -> None:
    for name in EXPECTED_FILES:
        path = HIDS_DIR / name
        raw = path.read_bytes()
        assert b"\x00" not in raw, f"{name} contains NUL byte"


def test_all_12_categories_represented() -> None:
    """读完 12 文件后，hids_category 集合必须等于 VALID_CATEGORIES。"""
    seen_cats: set[str] = set()
    for name in EXPECTED_FILES:
        items = _load(name)
        for item in items:
            seen_cats.add(item["hids_category"])
    assert seen_cats == VALID_CATEGORIES, (
        f"missing categories: {VALID_CATEGORIES - seen_cats}; unexpected: {seen_cats - VALID_CATEGORIES}"
    )


def main() -> int:
    tests = [
        ("files_exist", test_files_exist),
        ("min_count_per_file", test_min_count_per_file),
        ("total_count_meets_bid_requirement", test_total_count_meets_bid_requirement),
        ("required_fields_present", test_required_fields_present),
        ("external_ids_globally_unique_and_prefixed", test_external_ids_globally_unique_and_prefixed),
        ("hids_category_matches_filename", test_hids_category_matches_filename),
        ("hids_execution_mode_in_allowed_set", test_hids_execution_mode_in_allowed_set),
        ("optional_shapes", test_optional_shapes),
        ("no_nul_bytes_in_json", test_no_nul_bytes_in_json),
        ("all_12_categories_represented", test_all_12_categories_represented),
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
    print(f"\nTotal payloads across {len(EXPECTED_FILES)} files: {total_count}")
    if failures:
        print(f"\n{len(failures)} test(s) failed", file=sys.stderr)
        return 1
    print(f"\nAll {len(tests)} tests passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
