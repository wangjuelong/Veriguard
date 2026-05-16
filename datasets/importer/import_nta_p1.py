#!/usr/bin/env python3
"""
P1.2.a NTA 11 类用例 → Veriguard 导入脚本（IPv6 安全验证系统招标 §4）.

读取 datasets/importer/nta_p1/*.json 中的 11 类 TrafficPattern payload，通过
Veriguard REST API 的 upsert 端点幂等灌入 payloads 表。

用法:
    export VERIGUARD_URL=http://localhost:8080
    export VERIGUARD_TOKEN=<admin uuid token>
    python3 import_nta_p1.py [--dry-run] [--only <cat>] [--limit N]

依赖:
    pip install requests

幂等性:
    payloads upsert by payload_external_id (NTA-BRUTEFORCE-001 ..
    NTA-INFOLEAKAGE-030)；同一 external_id 重跑覆盖旧字段。

依赖的后端能力:
    PayloadType.TRAFFIC_PATTERN + TrafficPatternPayload 子类 + V25 迁移
    (payload_nta_* 列)。详见 veriguard-model TrafficPatternPayload.java.
"""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

import requests

# ---------- 配置 ----------

REPO_ROOT = Path(__file__).resolve().parent.parent
NTA_DIR = REPO_ROOT / "importer" / "nta_p1"

VERIGUARD_URL = os.environ.get("VERIGUARD_URL", "http://localhost:8080").rstrip("/")
VERIGUARD_TOKEN = os.environ.get("VERIGUARD_TOKEN", "")
HTTP_TIMEOUT = 30

CATEGORY_FILES = {
    "brute_force": "brute_force.json",
    "reverse_shell": "reverse_shell.json",
    "memory_webshell_inject": "memory_webshell_inject.json",
    "covert_tunnel": "covert_tunnel.json",
    "malicious_dns": "malicious_dns.json",
    "webshell_command_exec": "webshell_command_exec.json",
    "vuln_exploit": "vuln_exploit.json",
    "rat_c2": "rat_c2.json",
    "privilege_bypass": "privilege_bypass.json",
    "unauthorized_access": "unauthorized_access.json",
    "info_leakage": "info_leakage.json",
}

# ---------- HTTP 客户端 ----------


def session() -> requests.Session:
    if not VERIGUARD_TOKEN:
        sys.exit("ERROR: VERIGUARD_TOKEN env var is required")
    s = requests.Session()
    s.headers.update(
        {
            "Authorization": f"Bearer {VERIGUARD_TOKEN}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
    )
    s.cookies.set_policy(_NoCookiePolicy())
    return s


class _NoCookiePolicy:
    """拒绝累积任何 cookie，使 Bearer + 无 cookies 的 CSRF bypass 生效。"""

    def set_ok(self, cookie, request) -> bool:
        return False

    def return_ok(self, cookie, request) -> bool:
        return False

    def domain_return_ok(self, domain, request) -> bool:
        return False

    def path_return_ok(self, path, request) -> bool:
        return False

    netscape = True
    rfc2965 = False
    hide_cookie2 = False


def post(s: requests.Session, path: str, body: Any, dry_run: bool) -> Any:
    url = f"{VERIGUARD_URL}{path}"
    if dry_run:
        size = len(json.dumps(body, ensure_ascii=False))
        print(
            f"  [DRY-RUN] POST {path}  body={size}B  "
            f"ext={body.get('payload_external_id')}  "
            f"cat={body.get('nta_category')}  proto={body.get('nta_protocol')}"
        )
        return None
    r = s.post(url, json=body, timeout=HTTP_TIMEOUT)
    if r.status_code >= 400:
        raise SystemExit(
            f"ERROR POST {url} → {r.status_code}\n"
            f"  external_id: {body.get('payload_external_id')}\n"
            f"  request body (head): {json.dumps(body, ensure_ascii=False)[:600]}\n"
            f"  response (head): {r.text[:600]}"
        )
    if r.text:
        return r.json()
    return None


# ---------- 数据加载 ----------


def load_category(name: str) -> list[dict]:
    path = NTA_DIR / CATEGORY_FILES[name]
    if not path.exists():
        raise SystemExit(f"ERROR: missing dataset file {path}")
    with open(path, "r", encoding="utf-8") as fh:
        items = json.load(fh)
    if not isinstance(items, list):
        raise SystemExit(f"ERROR: {path} must contain a JSON array")
    return items


# ---------- 导入流程 ----------


def import_category(
    s: requests.Session, name: str, dry_run: bool, limit: int | None
) -> tuple[int, int]:
    """返回 (success_count, fail_count)."""
    items = load_category(name)
    if limit:
        items = items[:limit]

    print(f"\n--- {name}: {len(items)} payload(s) ---")
    success = 0
    failures: list[tuple[str, str]] = []
    for item in items:
        ext_id = item.get("payload_external_id", "<missing>")
        try:
            post(s, "/api/payloads/upsert", item, dry_run)
            success += 1
            if not dry_run:
                print(f"  OK  {ext_id}")
        except SystemExit as exc:
            failures.append((ext_id, str(exc)))
            print(f"  FAIL  {ext_id}: {exc}", file=sys.stderr)
            if not dry_run:
                raise

    print(f"  → success={success}  failures={len(failures)}")
    return success, len(failures)


def main() -> None:
    parser = argparse.ArgumentParser(description="Import NTA P1.2.a traffic-pattern payloads (11 cat × 30)")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="不发请求，仅打印将要发送的 body 大小 + external id + category + protocol",
    )
    parser.add_argument(
        "--only",
        choices=list(CATEGORY_FILES.keys()) + ["all"],
        default="all",
        help="只导入指定类别（默认 all）",
    )
    parser.add_argument("--limit", type=int, default=None, help="每类只导前 N 条（调试用）")
    args = parser.parse_args()

    if args.dry_run:
        print(f"DRY-RUN mode (target: {VERIGUARD_URL})")
        s = requests.Session()
    else:
        s = session()

    categories = list(CATEGORY_FILES.keys()) if args.only == "all" else [args.only]
    total_success = 0
    total_failures = 0
    for cat in categories:
        ok, fail = import_category(s, cat, args.dry_run, args.limit)
        total_success += ok
        total_failures += fail

    print(f"\n=== summary ===\n  success: {total_success}\n  failures: {total_failures}")
    if total_failures > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
