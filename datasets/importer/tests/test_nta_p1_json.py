#!/usr/bin/env python3
"""
P1.2.a NTA 数据集 JSON 校验测试（IPv6 安全验证系统招标 §4）.

可独立 main 运行：
    python3 datasets/importer/tests/test_nta_p1_json.py

也可被 pytest 收集（test_* 函数被 unittest.main 兼容）。

校验目标：
  - 11 个 JSON 文件存在（招标 §4 列出 11 类 NTA 攻击）
  - 每文件 ≥ 30 条 payload
  - 总数 ≥ 300（招标 §4 数量门槛）
  - 每条必备字段齐全：
        payload_type == "TrafficPattern"
        payload_external_id (唯一，前缀 "NTA-")
        payload_name / payload_description / payload_source / payload_status
        payload_platforms / payload_expectations / payload_domains 非空
        nta_category 严校到 11 类之一
        nta_protocol 严校到协议白名单
        nta_signature 非空
        nta_detection_hint 非空
  - payload_external_id 跨 11 文件全局唯一
  - 不含 NUL 字节（PG JSONB 兼容）
  - payload_domains 至少 1 个
  - 11 类全覆盖（与文件名一一对应）
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
NTA_DIR = REPO_ROOT / "importer" / "nta_p1"

EXPECTED_FILES = [
    "brute_force.json",
    "reverse_shell.json",
    "memory_webshell_inject.json",
    "covert_tunnel.json",
    "malicious_dns.json",
    "webshell_command_exec.json",
    "vuln_exploit.json",
    "rat_c2.json",
    "privilege_bypass.json",
    "unauthorized_access.json",
    "info_leakage.json",
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
    "nta_category",
    "nta_protocol",
    "nta_signature",
    "nta_detection_hint",
]

VALID_CATEGORIES = {
    "brute_force",
    "reverse_shell",
    "memory_webshell_inject",
    "covert_tunnel",
    "malicious_dns",
    "webshell_command_exec",
    "vuln_exploit",
    "rat_c2",
    "privilege_bypass",
    "unauthorized_access",
    "info_leakage",
}

VALID_PROTOCOLS = {
    "TCP",
    "UDP",
    "HTTP",
    "HTTPS",
    "TLS",
    "DNS",
    "ICMP",
    "SMB",
    "SSH",
    "DCE-RPC",
}

MIN_PER_FILE = 30
MIN_TOTAL = 300


def _load(name: str) -> list[dict]:
    path = NTA_DIR / name
    assert path.exists(), f"missing {path}"
    raw = path.read_text(encoding="utf-8")
    assert "\x00" not in raw, f"{name} contains NUL byte (PG JSONB incompatible)"
    items = json.loads(raw)
    assert isinstance(items, list), f"{name} must be a JSON array"
    return items


def test_files_exist() -> None:
    for name in EXPECTED_FILES:
        assert (NTA_DIR / name).exists(), f"missing dataset file {name}"
    assert len(EXPECTED_FILES) == 11, "must cover 11 NTA categories per 招标 §4"


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
            assert item["payload_type"] == "TrafficPattern", (
                f"{name}[{idx}] payload_type must be 'TrafficPattern', got {item['payload_type']}"
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
            assert ext_id.startswith("NTA-"), (
                f"{name}[{idx}] payload_external_id {ext_id!r} must start with 'NTA-'"
            )
            assert ext_id not in seen, (
                f"duplicate payload_external_id {ext_id}: first seen in {seen[ext_id]}, again in {name}[{idx}]"
            )
            seen[ext_id] = f"{name}[{idx}]"


def test_nta_category_matches_filename() -> None:
    """每个 JSON 内所有 entry 的 nta_category 必须等于文件名（去 .json）."""
    for name in EXPECTED_FILES:
        cat_from_filename = name.removesuffix(".json")
        assert cat_from_filename in VALID_CATEGORIES, f"unknown category in filename {name}"
        items = _load(name)
        for idx, item in enumerate(items):
            assert item["nta_category"] == cat_from_filename, (
                f"{name}[{idx}] nta_category={item['nta_category']!r} ≠ filename {cat_from_filename!r}"
            )


def test_nta_protocol_in_allowed_set() -> None:
    for name in EXPECTED_FILES:
        items = _load(name)
        for idx, item in enumerate(items):
            proto = item["nta_protocol"]
            assert proto in VALID_PROTOCOLS, (
                f"{name}[{idx}] nta_protocol={proto!r} not in {sorted(VALID_PROTOCOLS)}"
            )


def test_optional_shapes() -> None:
    """非必备但若存在则需正确：attack_patterns/list、pcap_path/nullable-str."""
    for name in EXPECTED_FILES:
        items = _load(name)
        for idx, item in enumerate(items):
            ap = item.get("payload_attack_patterns")
            assert isinstance(ap, list), f"{name}[{idx}] payload_attack_patterns must be list"
            pcap = item.get("nta_pcap_path")
            assert pcap is None or (isinstance(pcap, str) and pcap), (
                f"{name}[{idx}] nta_pcap_path must be null or non-empty string"
            )


def test_no_nul_bytes_in_json() -> None:
    for name in EXPECTED_FILES:
        path = NTA_DIR / name
        raw = path.read_bytes()
        assert b"\x00" not in raw, f"{name} contains NUL byte"


def test_all_11_categories_represented() -> None:
    """读完 11 文件后，nta_category 集合必须等于 VALID_CATEGORIES。"""
    seen_cats: set[str] = set()
    for name in EXPECTED_FILES:
        items = _load(name)
        for item in items:
            seen_cats.add(item["nta_category"])
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
        ("nta_category_matches_filename", test_nta_category_matches_filename),
        ("nta_protocol_in_allowed_set", test_nta_protocol_in_allowed_set),
        ("optional_shapes", test_optional_shapes),
        ("no_nul_bytes_in_json", test_no_nul_bytes_in_json),
        ("all_11_categories_represented", test_all_11_categories_represented),
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
