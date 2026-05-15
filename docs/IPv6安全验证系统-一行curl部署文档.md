# IPv6 安全验证系统 — 一行 curl 部署文档

> 招标锚点：§agent 部署与上线流程
> 整理日期：2026 年 5 月
> 用途：Veriguard Agent（C1，Rust）在 Linux / macOS / Windows 三平台的零配置一行命令部署、上线、运行、卸载操作指引

## 1. 部署形态总览

| 形态 | 适用场景 | 网络要求 | 命令 | 落地位置 |
| --- | --- | --- | --- | --- |
| **Mode A 在线一行 curl** | 标准生产 / 边界资产可访问平台 | Agent → Platform 出向 HTTPS | `curl -fsSL <平台>/api/agent/install/{token} \| bash` | `/usr/local/bin/veriguard-agent` |
| **Mode A PowerShell** | Windows 主机 / 域内统一推送 | Agent → Platform 出向 HTTPS | `iex (irm <平台>/api/agent/install/{token}/ps1)` | `C:\Program Files\Veriguard\veriguard-agent.exe` |
| **Mode A 手工二进制** | 离线 / 防火墙严控 / 自建 RPM 包 | 仅需下载阶段联网 | 见 §3.4 | 同 Mode A |
| **Mode C 离线包** | 完全断网 / 跨网段 / 政企内网 | 任意 | 见 §4 | 同 Mode A，但执行从 `.vpack` 文件读取 |

> 三种 Mode A 形态最终都调用同一个 `veriguard-agent init --bootstrap` 命令上线；区别仅在二进制送达方式。

## 2. 前置条件

### 2.1 平台侧

- Veriguard Platform 已部署且 `https://<平台>/api/health` 可访问。
- 管理员账号已通过 `/admin` 登录，具备 `WRITE AGENT` RBAC 权限。
- Agent binary 已通过 CI release pipeline 落到平台 classpath：`agents/veriguard-agent/{linux,macos,windows}/{x86_64,arm64}/veriguard-agent[.exe]`
  - **C1-Agent-3 阶段 release pipeline 上线前**：endpoint 返回 `404 agent binary not yet bundled`，需手工把 `cargo build --release` 产物 cp 到对应 classpath 子目录。

### 2.2 目标主机侧

| 平台 | 要求 |
| --- | --- |
| Linux | x86_64 或 aarch64；glibc ≥ 2.31（CentOS 8+ / Ubuntu 22.04+ / Debian 12+ / 国产 OS 麒麟 V10 / 统信 UOS 1060）；可访问 `https://<平台>` |
| macOS | x86_64 或 arm64（M1/M2/M3/M4）；macOS 13+ |
| Windows | x86_64 或 arm64；Windows 10 20H2+ / Windows Server 2019+ |
| 网络 | TCP 443 出向（HTTPS）；DNS 解析平台域名 |
| 权限 | 安装到 `/usr/local/bin` 或 `C:\Program Files\Veriguard` 需 sudo / Administrator |

## 3. 部署流程

### 3.1 第一步：管理员侧生成 onboard token

通过平台管理 API（管理员认证）创建 Agent 注册名额：

```bash
curl -fsSL -X POST "https://<平台>/api/agent/onboard/init" \
  -H "Authorization: Bearer <管理员token>" \
  -H "Content-Type: application/json" \
  -d '{
    "display_name": "edge-host-01",
    "capabilities": ["http_attack", "pcap_replay", "command_inject"],
    "allowed_modes": ["A", "C"]
  }'
```

返回（JSON 节选）：

```json
{
  "agent_id": "agt_01H...",
  "onboard_token": "8bc3a4...",       // 24h TTL
  "platform_url": "https://<平台>",
  "platform_id": "plt_01H...",
  "platform_ed25519_pub_b64": "...",  // 用于校验后续平台签名
  "platform_x25519_pub_b64": "...",   // 用于解密 Mode C .vpack
  "platform_cert_fingerprint_sha256": "...",
  "ttl_seconds": 86400
}
```

**字段说明：**
- `onboard_token` — 一次性 token，24 小时内有效，被 register 调用消耗后失效。
- `capabilities` — Agent 上线后宣称支持的能力集；平台调度时按此匹配。
- `allowed_modes` — `"A"` 在线 / `"C"` 离线，二者可任选其一或都给。

### 3.2 第二步：目标主机 Linux / macOS 一行命令

复制 `onboard_token` 到目标主机，执行：

```bash
TOKEN=8bc3a4...
PLATFORM=https://<平台>

curl -fsSL "$PLATFORM/api/agent/install/$TOKEN" | bash
```

脚本会自动完成：

1. 通过 `uname -s` 检测 OS（Linux → `linux` / Darwin → `macos`）。
2. 通过 `uname -m` 检测架构（`x86_64`/`amd64` → `x86_64`；`aarch64`/`arm64` → `arm64`）。
3. 从 `$PLATFORM/api/agent/install/binary/$OS/$ARCH` 下载二进制 + 读 `X-SHA256` 响应头。
4. 本地 `shasum -a 256` 校验完整性，不匹配立即退出。
5. `install -m 0755 → /usr/local/bin/veriguard-agent`（无写权限自动 `sudo`）。
6. 调用 `veriguard-agent init --bootstrap --onboard-token <token> --platform-url <平台>` 完成上线。

**安全提示（招标 S-2 防线）：** 生产环境建议先 `curl ... | less` 审阅脚本内容，确认 token 与平台 URL 正确后再 `| bash` 执行。

**自定义安装目录：**

```bash
VERIGUARD_INSTALL_DIR=/opt/veriguard/bin curl -fsSL "$PLATFORM/api/agent/install/$TOKEN" | bash
```

### 3.3 第二步：Windows PowerShell 一行命令

以 Administrator 启动 PowerShell：

```powershell
$Token = "8bc3a4..."
$Platform = "https://<平台>"

iex (irm "$Platform/api/agent/install/$Token/ps1")
```

脚本行为：

1. 通过 `$env:PROCESSOR_ARCHITECTURE` 检测架构（AMD64 → `x86_64`；ARM64 → `arm64`）。
2. 下载 `veriguard-agent.exe` + `X-SHA256` 头校验。
3. 落 `C:\Program Files\Veriguard\veriguard-agent.exe`（可通过 `$env:VERIGUARD_INSTALL_DIR` 覆盖）。
4. 调用 `& $Target init --bootstrap --onboard-token X --platform-url Y` 上线。

### 3.4 第二步（备选）：手工下载二进制（air-gapped）

适合无法直接 `curl | bash` 的严控环境。在**任意联网机器**：

```bash
# Linux x86_64 示例
curl -fsSL -o veriguard-agent \
  -D headers.txt \
  "https://<平台>/api/agent/install/binary/linux/x86_64"

# 校验 SHA-256
EXPECTED=$(awk -F': ' 'tolower($1)=="x-sha256"{gsub(/\r/,"",$2);print $2}' headers.txt)
ACTUAL=$(shasum -a 256 veriguard-agent | awk '{print $1}')
[ "$EXPECTED" = "$ACTUAL" ] || { echo "SHA mismatch"; exit 1; }
```

把 `veriguard-agent` 通过 USB / 内网传输落到目标主机，然后：

```bash
sudo install -m 0755 veriguard-agent /usr/local/bin/
sudo veriguard-agent init --bootstrap \
  --onboard-token <token> \
  --platform-url https://<平台>
```

### 3.5 第三步：上线验证

```bash
# 查看 Agent 状态文件
ls -la ~/.config/veriguard/    # Linux 默认 state_dir
# 应见：keys/sign.key, keys/enc.key, install-pack.json

# 查看 Agent 进程
ps -ef | grep veriguard-agent

# 平台管理后台 /admin/veriguard/agents 应能看到新 Agent
```

平台侧可执行：

```bash
curl -fsSL "https://<平台>/api/agents?display_name_contains=edge-host-01" \
  -H "Authorization: Bearer <管理员token>"
```

## 4. Mode C — 离线包部署

适合完全断网 / 跨网段隔离环境。

### 4.1 准备 install pack（管理员侧，联网）

```bash
curl -fsSL -X GET "https://<平台>/api/agent/onboard/bootstrap" \
  -H "Authorization: Bearer <onboard_token>" > install_pack.json
```

`install_pack.json` 包含：

| 字段 | 内容 |
| --- | --- |
| `schema_version` | `"1.0"` |
| `platform_url` | 平台 base URL |
| `platform_cert_fingerprint_sha256` | TLS 证书指纹 |
| `platform_ed25519_pub_b64` | 平台签名公钥 |
| `platform_x25519_pub_b64` | 平台加密公钥 |
| `onboard_token` | 24h TTL |
| `agent_label` | 显示名 |

### 4.2 离线主机 — 二进制 + install pack 一起送达

通过 USB / 内网传输：

```bash
sudo install -m 0755 veriguard-agent /usr/local/bin/
sudo install -m 0600 install_pack.json /etc/veriguard/

veriguard-agent init --install-pack /etc/veriguard/install_pack.json
```

Agent 会在 `~/.config/veriguard/`（或 `--state-dir` 指定路径）生成 Ed25519 sign + X25519 enc keypair，把公钥写回到 install pack 旁边的 `register_request.json`。

### 4.3 注册请求回传（管理员侧）

把 `register_request.json` 取回联网机器：

```bash
curl -fsSL -X POST "https://<平台>/api/agent/onboard/register" \
  -H "Content-Type: application/json" \
  -d @register_request.json
```

返回 `{"status":"registered","agent_id":"agt_..."}` 即上线成功。

### 4.4 离线 Mode C 任务执行

平台侧：

```bash
curl -fsSL -X POST "https://<平台>/api/agent/offline-pack/export" \
  -H "Authorization: Bearer <管理员token>" \
  -H "Content-Type: application/json" \
  -d '{"agent_id":"agt_...","attack_chain_run_id":"acr_..."}' \
  > tasks.vpack
```

`tasks.vpack` 是加签 + 加密的 JSON 包（X25519-ECDH + ChaCha20-Poly1305 IETF + Ed25519 签名）。USB 传送到离线 Agent 主机后：

```bash
# C1-Agent-3 完工后启用：
veriguard-agent pack --input tasks.vpack --output results.vresults
```

`results.vresults` 取回联网机器：

```bash
curl -fsSL -X POST "https://<平台>/api/agent/offline-pack/import" \
  -H "Authorization: Bearer <管理员token>" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @results.vresults
```

> Mode C 单包任务上限 1 000；通过分包 + 黑名单去重支持万级任务。

## 5. 后台 / 服务化运行（C1-Agent-3 待启动）

C1-Agent-3 阶段（A.8）将提供：

- **systemd unit**（Linux）：`veriguard-agent install --service` 自动写 `/etc/systemd/system/veriguard-agent.service` 并 `systemctl enable --now`。
- **launchd plist**（macOS）：自动写 `/Library/LaunchDaemons/io.veriguard.agent.plist`。
- **Windows service**：通过 `windows-service` crate 注册为系统服务。
- **uninstall** 子命令：清理服务、二进制、state_dir。
- **rotate-keys** 子命令：滚动 Ed25519 / X25519 密钥对。

**当前替代方案**（C1-Agent-3 前）：手工 systemd unit：

```ini
# /etc/systemd/system/veriguard-agent.service
[Unit]
Description=Veriguard Agent (C1)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=veriguard
Group=veriguard
ExecStart=/usr/local/bin/veriguard-agent run
Restart=on-failure
RestartSec=10
Environment=VERIGUARD_STATE_DIR=/var/lib/veriguard

[Install]
WantedBy=multi-user.target
```

```bash
sudo useradd -r -s /bin/false veriguard
sudo install -d -m 0700 -o veriguard -g veriguard /var/lib/veriguard
sudo systemctl daemon-reload
sudo systemctl enable --now veriguard-agent.service
sudo journalctl -u veriguard-agent -f
```

## 6. 卸载

### 6.1 Linux / macOS

```bash
# 停服务
sudo systemctl stop veriguard-agent && sudo systemctl disable veriguard-agent
sudo rm -f /etc/systemd/system/veriguard-agent.service

# 删二进制 + state
sudo rm -f /usr/local/bin/veriguard-agent
sudo rm -rf /var/lib/veriguard ~/.config/veriguard

# 平台侧吊销 agent（不再分配任务）
curl -fsSL -X DELETE "https://<平台>/api/agents/agt_..." \
  -H "Authorization: Bearer <管理员token>"
```

### 6.2 Windows

```powershell
Stop-Service veriguard-agent -ErrorAction SilentlyContinue
sc.exe delete veriguard-agent
Remove-Item -Force "C:\Program Files\Veriguard\veriguard-agent.exe"
Remove-Item -Recurse -Force "$env:ProgramData\Veriguard"
```

## 7. 故障排查

| 现象 | 排查 | 处置 |
| --- | --- | --- |
| `curl ... \| bash` 报 404 | endpoint 404 + body 含 `agent binary not yet bundled` | C1-Agent-3 release pipeline 未上线；手工 cp `cargo build --release` 产物到平台 classpath |
| SHA-256 mismatch | 下载中途网络问题 / 中间人 | 重试；持续不一致排查 TLS 中间盒；用 `--platform-cert-pin` 强校验 |
| `init --bootstrap` 报 `onboard_token_invalid_or_expired` | token 已被消耗 / 已过 24h TTL | 管理员重新生成新 token |
| `init --bootstrap` 卡住 / 超时 | 出向 HTTPS 被防火墙阻断 | 开通 Agent 主机 → 平台 域名 + IP 出向 443/TCP；或改 Mode C |
| Agent 启动但 `/admin/veriguard/agents` 见不到 | register 失败 / 签名验证不过 | 检查 `~/.config/veriguard/agent.log`；常见为 keypair 与平台不一致（重新 init）|
| Agent 上线但不接任务 | capability 未声明 / Agent 不在线 | 检查 init 时的 `--capabilities`；轮询日志确认 Agent 每 30s poll |
| Mode C `.vpack` 解析失败 | 签名 / 解密失败 / 黑名单已记录 | C1-Agent-3 `pack verify --debug tasks.vpack` 输出每步决策树；常见为 `pack_id` 已在 SQLite executed_packs 表（防重放）|

## 8. 招标交付截图占位

C1-Integration alpha 完工后回填：

- 管理员后台生成 onboard_token 截图
- Linux 主机 `curl | bash` 全过程截屏
- Windows PowerShell `iex (irm ...)` 全过程截屏
- 平台后台 `/admin/veriguard/agents` 看到 Agent 上线状态
- Mode C 离线 USB 全流程示意图（管理员 export .vpack → USB → 离线主机 → USB → 管理员 import .vresults）
- systemd 服务管理（A.8 完工后）

## 9. 引用

- API 实现：`veriguard-api/src/main/java/io/veriguard/rest/agent/AgentInstallScriptApi.java`
- Agent 命令行：`wangjuelong/veriguard-agent` `src/main.rs`（subcommands: `init`, `run`）
- Mode A onboard 服务：`veriguard-api/src/main/java/io/veriguard/service/AgentOnboardingService.java`（`ONBOARD_TOKEN_TTL = 24h`）
- Mode C `.vpack` 服务：`veriguard-api/src/main/java/io/veriguard/crypto/VpackSerializer.java`
- 一致性映射表：`docs/IPv6安全验证系统-攻击类型与ATTCK平台能力对照表.md`
