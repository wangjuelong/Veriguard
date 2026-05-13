#!/usr/bin/env python3
"""
PR B8 边界 7 类用例 → Veriguard 导入脚本

读取 ``datasets/importer/boundary_b8/*.json`` 中的 7 类 Nuclei-sourced web_attack
payload（SQLi / XSS / SSRF / RCE / LFI / FILE_UPLOAD / DEFAULT_LOGIN），通过
Veriguard REST API 的 ``POST /api/payloads/upsert`` 幂等灌入 ``payloads`` 表
（``payload_type = WebAttack`` ↔ PayloadType.WEB_ATTACK, PR B7 落地）。

用法::

    export VERIGUARD_URL=http://localhost:8080
    export VERIGUARD_TOKEN=<admin uuid token>
    python3 import_boundary_b8.py [--dry-run] \\
        [--only sqli|xss|ssrf|rce|lfi|file_upload|default_login|all] [--limit N]

依赖::

    pip install requests

幂等性::

    ``payloads`` upsert by ``payload_external_id`` (``B8-<CAT>-NNN``).

注意::

    - JSON 由 ``nuclei_to_boundary_b8.py`` 从上游 ``nuclei-templates`` 生成；
      重跑生成器会保持 external_id 稳定（按相对路径排序），同一条 nuclei id
      ↔ 同一条 ``B8-<CAT>-NNN``。
    - 失败立即退出（fail-fast），不允许半灌：见 [[project-ipv6-dev-env-workflow]] 5 大踩坑。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

import requests

# ---------- 配置 ----------

REPO_ROOT = Path(__file__).resolve().parent.parent
B8_DIR = REPO_ROOT / "importer" / "boundary_b8"

VERIGUARD_URL = os.environ.get("VERIGUARD_URL", "http://localhost:8080").rstrip("/")
VERIGUARD_TOKEN = os.environ.get("VERIGUARD_TOKEN", "")
# Some web_attack upserts touch attack_pattern lookups + JSONB headers/body — the
# Veriguard payload upsert can take 5-10s under load. Use a 120s timeout (override
# via VERIGUARD_HTTP_TIMEOUT) to ride out occasional GC pauses on dev.
HTTP_TIMEOUT = int(os.environ.get("VERIGUARD_HTTP_TIMEOUT", "120"))

CATEGORY_FILES = {
    "sqli": "sqli.json",
    "xss": "xss.json",
    "ssrf": "ssrf.json",
    "rce": "rce.json",
    "lfi": "lfi.json",
    "file_upload": "file_upload.json",
    "default_login": "default_login.json",
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
    """拒绝累积任何 cookie，使 Bearer + 无 cookies 的 CSRF bypass 生效。

    见 [[project-ipv6-dev-env-workflow]] 踩坑 #3。
    """

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
        print(f"  [DRY-RUN] POST {path}  body={size}B  ext={body.get('payload_external_id')}")
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
    path = B8_DIR / CATEGORY_FILES[name]
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
            if not dry_run and success % 25 == 0:
                print(f"  ... {success}/{len(items)} ({ext_id})")
        except SystemExit as exc:
            failures.append((ext_id, str(exc)))
            print(f"  FAIL  {ext_id}: {exc}", file=sys.stderr)
            if not dry_run:
                raise

    print(f"  → success={success}  failures={len(failures)}")
    return success, len(failures)


def main() -> None:
    parser = argparse.ArgumentParser(description="Import boundary B8 (Nuclei-derived) web-attack payloads")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="不发请求，仅打印 body 大小 + external id",
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
