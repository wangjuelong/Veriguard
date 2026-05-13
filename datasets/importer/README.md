# MITRE ATT&CK + ART → Veriguard 导入器

把本地 `datasets/mitre-attack/` + `datasets/atomic-red-team/` 数据通过 Veriguard REST API 幂等导入。

## 前置条件

1. **Veriguard 已运行**：
   ```sh
   cd veriguard-dev && docker compose up -d
   cd .. && mvn -pl veriguard-api -am spring-boot:run
   ```
   等到 `http://localhost:8080` 可访问。

2. **拿到 admin token**：在 `application.properties` 或环境变量里配置的 `veriguard.admin.token`（UUID）。
   如果还没配，加一行到 `veriguard-api/src/main/resources/application.properties`：
   ```properties
   veriguard.admin.token=00000000-0000-0000-0000-000000000001
   veriguard.admin.email=admin@veriguard.io
   veriguard.admin.password=ChangeMe123!
   veriguard.admin.encryption_key=ChangeMeChangeMeChangeMe
   veriguard.admin.encryption_salt=ChangeMe
   ```
   重启平台后生效。

3. **Python 3.10+ + 依赖**：
   ```sh
   cd datasets/importer
   pip install -r requirements.txt
   ```

## 使用

### 干跑（不发请求，只打印将要做什么）

```sh
python3 import_mitre_art.py --dry-run
```

### 真跑

```sh
export VERIGUARD_URL=http://localhost:8080
export VERIGUARD_TOKEN=00000000-0000-0000-0000-000000000001
python3 import_mitre_art.py
```

### 只导某一部分

```sh
python3 import_mitre_art.py --only mitre   # 只导 MITRE
python3 import_mitre_art.py --only art     # 只导 ART
```

### 调试小批量

```sh
python3 import_mitre_art.py --only art --limit 20
```

## 导入内容

| 数据集 | 目标表 | 数量 | 端点 |
|---|---|---:|---|
| MITRE Tactics (Enterprise + Mobile + ICS) | `kill_chain_phases` | 41 | `POST /api/kill_chain_phases/upsert`（批量） |
| MITRE Techniques | `attack_patterns` | ~1 166 | `POST /api/attack_patterns/upsert`（批量） |
| ART atomic_tests | `payloads` | ~1 700 (executor 命中) | `POST /api/payloads/upsert`（单条循环） |

## 字段映射

详见 `datasets/README.md` 的"与 Veriguard 数据模型的映射"小节。

## 幂等性

- **kill_chain_phases**：服务端按 `(phase_kill_chain_name, phase_shortname)` 去重，重跑 OK。
- **attack_patterns**：按 `attack_pattern_external_id`（如 `T1059.001`）去重。
- **payloads**：按 `payload_external_id`（ART `auto_generated_guid`）去重。

可放心重复执行。

## 不在范围内（后续手动 / 工具补）

- **Sub-technique 父子绑定**：脚本上传时 `attack_pattern_parent` 留空，绑定由人工或后续脚本补（按 external_id `.` 前缀推父 UUID 后 PUT 更新）。
- **payload_domains**：脚本传空集合，让用户在 UI 上分配业务域；如需自动按关键字归类，扩展 `PRESET_DOMAIN_KEYWORDS` map.
- **payload_tags**：脚本传空，可后续按 ART `tags`（如 `windows-credential-dumping`）转 Tag。
- **`manual` executor / `iaas:*` / `office-365` 等 ART 用例**：脚本会 skip 并计数，约 ~100 条。

---

## 边界用例补强（PR B6）

招标 §3.5 边界防护"12 类攻击 ≥ 500 用例"门槛中，**Nuclei + CRS** 已直接覆盖 9 类（978 + 171 = 1149 条）。本 PR 自建以下 5 类共 **155 条** web 攻击 payload，存于 `datasets/importer/boundary_b6/*.json`：

| 类别 | 文件 | 条数 | 关键变体 |
|---|---|---:|---|
| CSRF | `boundary_b6/csrf.json` | 31 | form/JSON/multipart POST、SameSite/Origin/Referer 绕过、token mismatch、双提交、GraphQL/PATCH/DELETE/HEAD、CSWSH、HPP |
| 大包上传 | `boundary_b6/oversized_upload.json` | 31 | 2 MB JSON、100 MB multipart、chunked、gzip/zip bomb、长 URL/header、slowloris、RUDY、HTTP/2 CONTINUATION flood、Rapid Reset (CVE-2023-44487)、TE-CL smuggling、tar slip |
| SSTI | `boundary_b6/ssti.json` | 31 | Jinja2/Twig/Velocity/FreeMarker/Mako/Smarty/Pebble/Handlebars/ERB/Tornado/Thymeleaf+SpEL/Razor/Liquid/Pug/Slim/Nunjucks 多 engine + RCE 链 + filter/concat/encoded 绕过 |
| XXE | `boundary_b6/xxe.json` | 31 | classic SYSTEM、parameter entity、OOB、billion laughs、SVG/DOCX/XLSX/SOAP/SAML/RSS/WebDAV/GraphQL、php:// / expect:// / gopher:// / jar://、XInclude、XSLT、UTF-7/UTF-16 |
| 暴力破解 | `boundary_b6/brute_force.json` | 31 | Basic/form/OTP/PIN/JWT/API key/reset token/邀请码、password spray、credential stuffing、XFF/X-Real-IP rate-limit 旁路、case toggle、TOR、长密码 DoS、IDOR scan、WordPress、digest auth |

每条字段对齐 `WebAttackContent` (`veriguard-api/src/main/java/io/veriguard/injectors/web_attack/model/WebAttackContent.java`)：

```json
{
  "payload_type": "WebAttack",
  "payload_external_id": "B6-CSRF-001",
  "payload_name": "CSRF: form POST without token",
  "payload_description": "...",
  "payload_source": "COMMUNITY",
  "payload_status": "VERIFIED",
  "payload_platforms": ["Generic"],
  "payload_execution_arch": "ALL_ARCHITECTURES",
  "payload_expectations": ["PREVENTION", "DETECTION"],
  "payload_attack_patterns": [],
  "payload_domains": [{"domain_id": "8144f32d-c2cf-43e0-a999-358628027817", "domain_name": "To classify", "domain_color": "#FFFFFF"}],
  "payload_tags": ["b6", "csrf", "..."],
  "web_request_method": "POST",
  "web_request_url": "/api/transfer",
  "web_request_headers": [{"name": "Content-Type", "value": "application/x-www-form-urlencoded"}],
  "web_request_body": "amount=1000&to=attacker_account",
  "web_request_body_type": "form",
  "expected_status_codes": [403],
  "expected_body_regex": "(?i)(csrf|forbidden)"
}
```

### 使用 importer

```sh
cd datasets/importer
export VERIGUARD_URL=http://localhost:8080
export VERIGUARD_TOKEN=00000000-0000-0000-0000-000000000001

# 干跑
python3 import_boundary_b6.py --dry-run

# 只导一类
python3 import_boundary_b6.py --only csrf
python3 import_boundary_b6.py --only oversized
python3 import_boundary_b6.py --only ssti
python3 import_boundary_b6.py --only xxe
python3 import_boundary_b6.py --only brute

# 全量真跑
python3 import_boundary_b6.py
```

幂等 key：`payload_external_id`（B6-CSRF-001 .. B6-BRUTE-031）。

### JSON 校验测试

```sh
python3 datasets/importer/tests/test_boundary_b6_json.py
```

校验 5 文件存在、每文件 ≥ 30 条、必备字段齐全、external_id 全局唯一、无 NUL 字节。

### 已知限制（后续 PR 补）

`payload_type = "WebAttack"` 当前**未在** `PayloadType` 枚举（`COMMAND/EXECUTABLE/FILE_DROP/DNS_RESOLUTION/NETWORK_TRAFFIC`）中注册，因此 `import_boundary_b6.py` 真跑会被 `PayloadUpsertInput` 校验拒绝。本 PR 仅交付：

1. **5 类 155 条** web 攻击样本数据（机读 JSON）
2. **importer 框架 + dry-run 验证** + **JSON 结构 / 唯一性 / NUL 字节** 单元测试

后续 PR 可在 `PayloadType` 加入 `WebAttack` 后让本 importer 真正落库；或独立实现一个 `WebAttackPayloadCatalog` 表 + 读取这批 JSON 的 collector，用法不变。

---

## 流量侧 pcap 脚手架（`parse_pcap_traffic.py`）

把 `datasets/PCAP-ATTACK/` + `datasets/malware-traffic-analysis-pcaps/` + `datasets/suricata-verify/tests/` 的 pcap 文件解析 + 归类后输出 JSONL，**不直接上传**——等 PR-D 加完 NetworkTraffic schema 改动后再换 upload 入口。

### 依赖

```sh
pip install scapy pyyaml
```

### 使用

```sh
cd datasets/importer

# 干跑一个数据集，限 5 个 pcap，打印详细记录
python3 parse_pcap_traffic.py --dataset pcap-attack --limit 5 --pretty

# 全量扫一个数据集
python3 parse_pcap_traffic.py --dataset pcap-attack

# 全部三个数据集，输出 JSONL
python3 parse_pcap_traffic.py --dataset all --output /tmp/pcap_payloads.jsonl
```

### 输出 JSONL 字段

每行一个 payload-ready 记录：

```json
{
  "payload_name": "C2_Foudre_Backdoor_DGA",
  "payload_external_id": "pcap-pcap-attack-<sha1>",
  "payload_type": "NetworkTraffic",
  "payload_source": "COMMUNITY",
  "payload_status": "UNVERIFIED",
  "payload_platforms": ["Generic"],
  "payload_expectations": ["PREVENTION", "DETECTION"],
  "payload_attack_patterns": ["T1071.004", "T1568.002"],
  "payload_tags": ["pcap-attack"],
  "network_traffic_ip_src": "10.0.2.15",
  "network_traffic_ip_dst": "185.56.137.138",
  "network_traffic_port_src": 49727,
  "network_traffic_port_dst": 80,
  "network_traffic_protocol": "TCP",
  "_future_pcap_file": "PCAP-ATTACK/...",
  "_future_network_traffic_tuples": [{}, {}],
  "_zhaobiao_category": ["恶意域名解析", "远控木马执行"],
  "_kill_chain_phase_shortname": "command-and-control",
  "_source_dataset": "pcap-attack"
}
```

### PR-D 还需的 Veriguard schema 改动

| 改动 | 现状 | 招标条款 |
|---|---|---|
| `NetworkTraffic` 加 `pcap_file` FK → Document | ❌ 无 | §4 上传 pcap 流量包 |
| `NetworkTraffic` 把单四元组改为 List<Tuple> JSONB | ❌ 单组（5 个标量） | §4 同一用例多个端口不同四元组 |
| 或新建 `PcapReplay` payload type 单独承载 | — | 替代方案 |

→ schema 落地后，本脚本的 `_future_*` 字段直接 unwrap 即可作为 upsert 输入；`upload_pcap_payloads.py`（待写）读取 JSONL → POST `/api/payloads/upsert`。

### 招标 §4 11 类自动归类（启发式 / 待人工校准）

脚本按文件名 / 父目录 lowercase 匹配 `ZHAOBIA_TRAFFIC_CATEGORIES` 关键字表（见脚本顶部）。已知问题：

- **malware-traffic-analysis 命名偏 EK family**（Angler-EK / Rig-EK / Fiesta-EK 等），导致大量 pcap 命中"高危漏洞利用"，其他 10 类偏少
- **EK pcap 实际通常涉及多个攻击阶段**（漏洞利用 → drop trojan → C2 通信 → exfil），可由用户在 Veriguard UI 上手动给 payload 加多个 tag
- **PCAP-ATTACK 26 个 pcap 标签精准**，可作为分类校准黄金集

### ATT&CK technique 自动标注

脚本自动推断 `payload_attack_patterns`（如 `T1071.004`、`T1568.002`），与 MITRE ATT&CK collector 已导入的 `attack_patterns` 表 `external_id` 直接关联。

## 故障排查

- `401 Unauthorized` — token 错或没设；检查 `veriguard.admin.token` 配置一致
- `404 Not Found POST /api/...` — 平台路由没注册，确认 `mvn spring-boot:run` 的是最新 main
- `400 MethodArgumentNotValidException` — 字段名 / 枚举值不匹配，看脚本对应输入 DTO 类（`PayloadUpsertInput.java` 等）核对
- `409 Conflict` 或 unique 约束 — 重复 external_id；多半是 ART 同一 GUID 在两个 technique 下出现，删 ART 端冲突项
- payload 大批失败 — 用 `--limit 5` 先验单条，看响应错误
