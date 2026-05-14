# Veriguard Agent + Implant Fork C1 + C2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fork OpenBAS-Platform/{agent,implant} 为 `wangjuelong/veriguard-{agent,implant}` 两个 Go 仓，加 Veriguard 主仓 Java 改造，把 §3 边界 / §4 流量 / §5 主机三场景从"骨架 + mock"升级到"真打 + 真反查"；C2 完成 §5.1 12 类映射表 + 真靶机演练 + 7 份部署文档。

**Architecture:**
- **3 Stream 并行**：A=veriguard-agent (Go, critical path ~3.0 周) / B=veriguard-implant (Go, ~1.0 周) / C=veriguard-api Java 改造 (~2.0 周)
- **契约先定**：Spec §3.3.2 CLI / §3.3.3 NDJSON Pipe / §3.5.1 REST / §3.5.2-3 .vpack/.vresults schema 已全部定型，3 Stream 可在 mock 顶替下独立开发
- **加密**：NaCl box (X25519 + ChaCha20-Poly1305) + Ed25519 签名；`.vpack`/`.vresults` JSON envelope
- **通信**：Mode A 在线 HTTPS poll（含一行 curl 安装）+ Mode C 离线 sneakernet 兜底
- **Fork 一次性脱钩**：不跟上游 patch / 不 cherry-pick

**Tech Stack:**
- **Go 1.22+**：`golang.org/x/crypto/{ed25519,nacl/box,curve25519}` / `modernc.org/sqlite` / `gopkg.in/yaml.v3` / `github.com/spf13/cobra`
- **Java 21 / Spring Boot 3.3.7**：Jackson / JPA / Hibernate JsonType / `org.bouncycastle:bcprov-jdk18on`（Ed25519 + X25519 + ChaCha20-Poly1305）/ Mockito
- **CI**：GitHub Actions matrix（Linux/Win/macOS × x86_64/arm64）

**Spec 出处**：`docs/superpowers/specs/2026-05-14-veriguard-agent-implant-fork-c1-c2-design.md` (commit `43f7a293e`, draft PR #55)

---

## 0. 准备工作 + 全局约定

### 0.1 环境与锁

- **Master 永不动**：`5d7e05da6` 锁定，所有 PR base=`main`
- **当前 main**：`6f0fd631d`（B8 已 merged）
- **下一可用 Flyway 版本**：V19 + V20（见 spec §3.6）
- **Java 21**：所有 mvn 命令前 `export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- **Go 1.22+**：两 Go 仓 `go.mod` 声明 `go 1.22`

### 0.2 Commit 风格

- Chinese 前缀：`执行：`/`修复：`/`设计：`/`测试：`
- 每 commit 末尾：`Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`
- **不混用 NUL byte**（即使 commit message 内也不能含 `\x00`；spec workflow 经历过此坑）

### 0.3 Fork Baseline（W1 Day 1 完成后填入）

```
veriguard-agent     ← OpenBAS-Platform/agent     @ <commit_hash>  (本 plan 执行时填)
veriguard-implant   ← OpenBAS-Platform/implant   @ <commit_hash>  (本 plan 执行时填)
```

两仓 README 顶部第一行写明，**仅作 LICENSE/attribution 用**，无后续同步。

### 0.4 Worktree

```bash
cd /Users/lamba/github/Veriguard
git fetch origin
git worktree add .claude/worktrees/c1-c2-impl feat/c1-c2-veriguard-agent-implant-fork
cd .claude/worktrees/c1-c2-impl
```

---

## 1. 文件结构

### 1.1 `wangjuelong/veriguard-agent` (新仓)

```
veriguard-agent/
├── cmd/veriguard-agent/main.go                    # CLI 入口 + cobra 命令
├── internal/
│   ├── config/config.go                           # CLI flags + env loading
│   ├── config/config_test.go
│   ├── crypto/
│   │   ├── ed25519.go                             # sign / verify
│   │   ├── ed25519_test.go
│   │   ├── x25519_box.go                          # nacl/box seal / open
│   │   ├── x25519_box_test.go
│   │   ├── keys.go                                # keypair 生成 / 持久 / 加载
│   │   └── keys_test.go
│   ├── transport/
│   │   ├── poll.go                                # Mode A HTTPS long-poll
│   │   ├── poll_test.go
│   │   ├── sign.go                                # X-Veriguard-Signature header
│   │   ├── sign_test.go
│   │   └── proxy.go                               # HTTPS_PROXY env handling
│   ├── pack/
│   │   ├── vpack.go                               # .vpack 解析 + 校签 + 解密
│   │   ├── vpack_test.go
│   │   ├── vresults.go                            # .vresults 签 + 加密
│   │   ├── vresults_test.go
│   │   ├── blacklist.go                           # SQLite executed_packs.db
│   │   ├── blacklist_test.go
│   │   ├── multi.go                               # 目录 lex 排序串行
│   │   └── multi_test.go
│   ├── capabilities/
│   │   ├── registry.go                            # capability 注册中心
│   │   ├── http_attack.go                         # §3 边界 - agent 内置 net/http
│   │   ├── http_attack_test.go
│   │   ├── pcap_replay.go                         # §4 流量 - fork tcpreplay
│   │   ├── pcap_replay_test.go
│   │   ├── command_inject.go                      # §5 主机 - drop implant + 启
│   │   ├── command_inject_test.go
│   │   ├── implant_drop.go                        # 通用 implant 落盘
│   │   └── implant_drop_test.go
│   ├── implant/
│   │   ├── manager.go                             # 下载 / 启停 / pipe read
│   │   ├── manager_test.go
│   │   └── pipe.go                                # named pipe 跨平台抽象
│   ├── onboard/
│   │   ├── init.go                                # install_pack 加载
│   │   ├── init_test.go
│   │   ├── bootstrap.go                           # 一行 curl 入口
│   │   ├── bootstrap_test.go
│   │   └── register.go                            # 注册请求 (在线 / 离线)
│   ├── service/
│   │   ├── systemd_linux.go                       # systemd unit
│   │   ├── launchd_darwin.go                      # launchd plist
│   │   └── windows.go                             # Windows service
│   ├── state/
│   │   ├── sqlite.go                              # SQLite 持久层
│   │   └── sqlite_test.go
│   └── logging/logger.go                          # structured JSON logs
├── build/
│   ├── Makefile                                   # cross-compile 6 matrix
│   └── ci/
│       └── build-release.yml                      # GitHub Actions
├── testdata/
│   ├── install_pack_valid.json
│   ├── pack_valid.vpack
│   ├── pack_signature_tampered.vpack
│   ├── pack_wrong_agent.vpack
│   └── pack_oversize.vpack
├── go.mod
├── go.sum
├── README.md                                      # 头部含 fork baseline 声明
└── LICENSE                                        # Apache 2.0（继承上游）
```

### 1.2 `wangjuelong/veriguard-implant` (新仓)

```
veriguard-implant/
├── cmd/veriguard-implant/main.go                  # 单入口，一次性短命
├── internal/
│   ├── payload/
│   │   ├── command.go                             # shell command 执行
│   │   ├── command_test.go
│   │   ├── executable.go                          # drop + execve 二进制
│   │   ├── executable_test.go
│   │   ├── filedrop.go                            # 落盘文件 + 权限
│   │   ├── filedrop_test.go
│   │   ├── dns_resolution.go                      # DNS A/AAAA/MX 查询
│   │   ├── dns_resolution_test.go
│   │   ├── network_traffic.go                     # 自定义 TCP/UDP/ICMP 包
│   │   └── network_traffic_test.go
│   ├── result/
│   │   ├── writer.go                              # NDJSON pipe writer
│   │   ├── writer_test.go
│   │   └── chunked.go                             # stdout/stderr chunk 拆分
│   ├── cleanup/
│   │   ├── self_delete_linux.go                   # POSIX unlink-after-exec
│   │   ├── self_delete_darwin.go
│   │   └── self_delete_windows.go                 # MoveFileExW + reboot delete
│   ├── platform/
│   │   ├── linux.go                               # /proc, fork/exec, named pipe
│   │   ├── windows.go                             # CreateProcessW, named pipe
│   │   └── darwin.go
│   └── logging/logger.go
├── build/Makefile + ci/build-release.yml
├── testdata/
│   ├── eicar.txt                                  # EICAR 标准杀软测试样本
│   └── webshell_sample.php
├── go.mod
├── go.sum
├── README.md
└── LICENSE
```

### 1.3 Veriguard 主仓 (Java 改动)

```
veriguard-api/src/main/java/io/veriguard/
├── crypto/                                         # 新包
│   ├── Ed25519SignatureService.java
│   ├── X25519BoxService.java
│   ├── VpackSerializer.java
│   ├── VresultsSerializer.java
│   └── CryptoException.java
├── injectors/
│   ├── command_inject/                             # 新包
│   │   ├── CommandInjectContract.java
│   │   ├── CommandInjectExecutor.java
│   │   ├── model/CommandInjectContent.java
│   │   └── service/CommandInjectDispatchService.java
│   ├── pcap_replay/PcapReplayExecutor.java          # 修改：去骨架
│   └── web_attack/service/WebAttackDispatchService.java  # 修改：去 TODO
├── combination/executor/HttpInjectExecutor.java     # 修改：去 UnsupportedOperationException
├── rest/agent/                                      # 新包（含若干新 Api 类）
│   ├── AgentOnboardApi.java
│   ├── AgentOfflinePackApi.java
│   ├── AgentTaskQueueApi.java
│   ├── AgentImplantDownloadApi.java
│   └── AgentInstallScriptApi.java
├── audit/                                          # 新包
│   └── OfflinePackAuditService.java
└── service/AgentService.java                       # 修改：严校 capability

veriguard-api/src/main/resources/db/migration/
├── V19__agent_crypto_keys.sql                      # 新增
└── V20__offline_pack_audit.sql                     # 新增

veriguard-api/src/main/resources/agents/
├── veriguard-agent/{linux,macos,windows}/{arm64,x86_64}/
│   └── veriguard-agent[.exe]                       # CI 拉取替换 .keep
└── veriguard-implant/{linux,macos,windows}/{arm64,x86_64}/  # 新目录
    └── veriguard-implant[.exe]

veriguard-api/src/test/java/io/veriguard/
├── crypto/                                          # 新增单测
└── rest/agent/                                      # 新增单测

veriguard-dev/
└── docker-compose.演练.yml                          # C2 新增
```

---

## 2. PR 切分建议

**单 PR 太大不可 review**（C1 改动 ~70 文件 + 2 新仓 + 1k+ 行测试）。**推荐拆 7 PR**：

| PR | 内容 | Stream | 工作量 | 依赖 |
| --- | --- | --- | --- | --- |
| #55 | spec ✓ | - | done | - |
| **C1-Agent-1** | veriguard-agent 仓建 + crypto + state + onboarding (Phase A.1-A.3) | A | ≈ 1.5 周 | spec merged |
| **C1-Agent-2** | agent capabilities + Mode A transport (Phase A.4-A.5) | A | ≈ 1.0 周 | C1-Agent-1 / C1-Implant |
| **C1-Implant** | veriguard-implant 仓建 + 5 payload + NDJSON writer + service | B | ≈ 1.0 周 | spec merged（并行）|
| **C1-Platform-1** | crypto services + V19/V20 + 5 REST 模块 (Phase C.1-C.7) | C | ≈ 1.2 周 | spec merged（并行）|
| **C1-Platform-2** | 4 Executor 真接通 + audit service (Phase C.8-C.12) | C | ≈ 0.8 周 | C1-Platform-1 |
| **C1-Integration** | 端到端 Mode A + Mode C + 安全测 T-1~T-8 + 一行 curl 安装 | A+B+C | ≈ 0.5 周 | 上述 4 PR all alpha |
| **C2** | 12 类映射 + docker-compose.演练.yml + 7 部署文档 | C2 | ≈ 0.5 周 | C1-Integration alpha |

**单人执行顺序**：spec → C1-Implant → C1-Agent-1 → C1-Agent-2 → C1-Platform-1 → C1-Platform-2 → C1-Integration → C2

**2 人并行**：
- 人 1: C1-Agent-1 → C1-Agent-2 → C1-Integration (W1-W4)
- 人 2: C1-Implant (W1) → C1-Platform-1 (W2-W3) → C1-Platform-2 (W3) → C2 (W4) → 帮 Integration

---

## 3. Checkpoints + 风险节点

| Checkpoint | 时机 | 验证项 | 风险与对策 |
| --- | --- | --- | --- |
| **CP-W1** | W1 结束 | C1-Implant 通过 / C1-Agent-1 crypto+onboarding 单测通过 / V19+V20 落库 | 风险：Go 1.22 跨编译 macOS arm64 在 Linux runner 失败 → 用 GitHub Actions 原生 macos-latest runner |
| **CP-W2** | W2 结束 | C1-Agent-2 Mode A 单测通过（mock platform）/ C1-Platform-1 REST endpoint mock client 单测通过 | 风险：NaCl box Go ↔ Java 互操作问题 → W1 提前用 testdata 跨语言互测 |
| **CP-W3** | W3 结束 | 4 Executor 真接通；agent 在 dev 容器内 install_pack onboarding 跑通 | 风险：systemd unit 在 docker container 内不可启 → 用 `--no-service` flag 跳过；W5 在真 VM 测 |
| **CP-W4** | W4 结束 | 集成 Scenario A1/A2/C1/C2/C3 通过；安全测 T-1~T-8 通过；一行 curl 在 3 平台冒烟 | 风险：mock-waf 规则不准 → 用 ModSecurity OWASP Core Rule Set 简化版 |
| **CP-W5 (招标演示就绪)** | W5 结束 | 演练 5 章节全跑通；3 份截图素材入档；7 份文档完成 | 风险：Win Server LTSC 容器 ARM64 不支持 → 改用 amd64 only |

---

## 4. Phase 0: Setup (W1 Day 1-2)

### Task 0.1: Fork OpenBAS-Platform/agent

**Files:** (no local; remote operation)

- [ ] **Step 1: 在 GitHub Web UI fork OpenBAS-Platform/agent 为 wangjuelong/veriguard-agent**

```
访问 https://github.com/OpenBAS-Platform/agent
点 Fork → wangjuelong/veriguard-agent
```

- [ ] **Step 2: clone fork 到本地 dev 目录**

```bash
cd /Users/lamba/github
git clone https://github.com/wangjuelong/veriguard-agent.git
cd veriguard-agent
git log -1 --format='%H %s' > /tmp/agent-fork-baseline.txt
cat /tmp/agent-fork-baseline.txt   # 记下 baseline commit
```

- [ ] **Step 3: 落 baseline 到 spec § 2.6**

```bash
# 回到 Veriguard 主仓 worktree
cd /Users/lamba/github/Veriguard/.claude/worktrees/c1-c2-impl
# 编辑 docs/superpowers/specs/2026-05-14-veriguard-agent-implant-fork-c1-c2-design.md
# 找到 § 2.6 Baseline commit 段
# 把 "<C1 W1 锁定时的 commit hash>" 占位符替换为真实 hash
git add docs/superpowers/specs/2026-05-14-veriguard-agent-implant-fork-c1-c2-design.md
git commit -m "设计：锁定 veriguard-agent baseline commit

记 OpenBAS-Platform/agent fork 时的上游 commit 入 spec § 2.6。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 0.2: Fork OpenBAS-Platform/implant

同 Task 0.1，目标 `wangjuelong/veriguard-implant`，落实 baseline 到 spec。

### Task 0.3: Veriguard-agent 仓初始化

**Files:**
- Create: `veriguard-agent/README.md`
- Create: `veriguard-agent/go.mod`
- Modify: `veriguard-agent/LICENSE`（继承上游 Apache 2.0）

- [ ] **Step 1: README 顶部加 baseline 声明**

```markdown
# veriguard-agent

Veriguard 平台自有验证 Agent —— 招标 §9.2 自建模块。

## Upstream attribution

Forked from [OpenBAS-Platform/agent](https://github.com/OpenBAS-Platform/agent) at commit `<填入 baseline hash>`.

**Fork 后已一次性脱钩**，后续两仓代码完全独立演化，不跟上游 patch / 不做 cherry-pick / 不强求协议兼容。上游归属仅作 LICENSE Apache 2.0 attribution 用途。

## Build

```
make all                 # 6 binary matrix
```

详见 spec：`docs/superpowers/specs/2026-05-14-veriguard-agent-implant-fork-c1-c2-design.md`
```

- [ ] **Step 2: 改 go.mod module 路径**

```bash
cd /Users/lamba/github/veriguard-agent
# 上游可能叫 github.com/openbas-platform/agent；改为：
sed -i.bak 's|github.com/openbas-platform/agent|github.com/wangjuelong/veriguard-agent|g' go.mod
# 删旧 LICENSE 中 Filigran 标识只保留 Apache 2.0 全文
# 不删上游 NOTICE 文件（如有），加一段 attribution
```

- [ ] **Step 3: 删上游与本设计冲突的子目录**

```bash
# 上游可能有 internal/auth/api_key.go 之类 - 删
rm -rf internal/auth/api_key*.go
# 删上游 CI workflow
rm -rf .github/workflows/
mkdir -p .github/workflows/
# 之后由 Task A.8 生成新的
```

- [ ] **Step 4: 初次 commit**

```bash
git add -A
git commit -m "执行：veriguard-agent fork 一次性脱钩 + module 重命名 + 删冗余 CI"
git push
```

### Task 0.4: Veriguard-implant 仓初始化

同 Task 0.3，目标 `veriguard-implant`：README baseline 声明、改 go.mod、删冲突文件、初次 commit。

---

## 5. Phase A: Stream A — veriguard-agent (W1-W3)

### A.1 Crypto 模块 (W1, security-critical)

#### Task A.1.1: Ed25519 keypair 生成 + 签名 + 验签

**Files:**
- Create: `internal/crypto/ed25519.go`
- Create: `internal/crypto/ed25519_test.go`

- [ ] **Step 1: 写失败测试**

```go
// internal/crypto/ed25519_test.go
package crypto

import (
    "testing"
)

func TestEd25519_SignVerify_Roundtrip(t *testing.T) {
    priv, pub, err := GenerateEd25519()
    if err != nil {
        t.Fatalf("generate: %v", err)
    }
    msg := []byte("test message")
    sig := SignEd25519(priv, msg)
    if !VerifyEd25519(pub, msg, sig) {
        t.Fatal("verify failed")
    }
}

func TestEd25519_Verify_TamperedMessage_Rejects(t *testing.T) {
    priv, pub, _ := GenerateEd25519()
    sig := SignEd25519(priv, []byte("original"))
    if VerifyEd25519(pub, []byte("tampered"), sig) {
        t.Fatal("verify should have failed on tampered message")
    }
}

func TestEd25519_Verify_TamperedSignature_Rejects(t *testing.T) {
    priv, pub, _ := GenerateEd25519()
    sig := SignEd25519(priv, []byte("msg"))
    sig[0] ^= 0xFF
    if VerifyEd25519(pub, []byte("msg"), sig) {
        t.Fatal("verify should have failed on tampered signature")
    }
}
```

- [ ] **Step 2: 跑测验证 FAIL**

```bash
go test ./internal/crypto/ -run TestEd25519 -v
# Expected: FAIL — undefined: GenerateEd25519 / SignEd25519 / VerifyEd25519
```

- [ ] **Step 3: 实现最小代码**

```go
// internal/crypto/ed25519.go
package crypto

import (
    "crypto/ed25519"
    "crypto/rand"
)

type Ed25519PrivateKey []byte
type Ed25519PublicKey []byte
type Ed25519Signature []byte

// GenerateEd25519 returns (priv, pub, err).
func GenerateEd25519() (Ed25519PrivateKey, Ed25519PublicKey, error) {
    pub, priv, err := ed25519.GenerateKey(rand.Reader)
    if err != nil {
        return nil, nil, err
    }
    return Ed25519PrivateKey(priv), Ed25519PublicKey(pub), nil
}

func SignEd25519(priv Ed25519PrivateKey, msg []byte) Ed25519Signature {
    return ed25519.Sign(ed25519.PrivateKey(priv), msg)
}

func VerifyEd25519(pub Ed25519PublicKey, msg []byte, sig Ed25519Signature) bool {
    if len(pub) != ed25519.PublicKeySize || len(sig) != ed25519.SignatureSize {
        return false
    }
    return ed25519.Verify(ed25519.PublicKey(pub), msg, sig)
}
```

- [ ] **Step 4: 跑测验证 PASS**

```bash
go test ./internal/crypto/ -run TestEd25519 -v
# Expected: PASS（3 个测试）
```

- [ ] **Step 5: Commit**

```bash
git add internal/crypto/ed25519.go internal/crypto/ed25519_test.go
git commit -m "执行：crypto - Ed25519 keypair + 签名 + 验签"
```

#### Task A.1.2: X25519 + NaCl Box 加密 / 解密

**Files:**
- Create: `internal/crypto/x25519_box.go`
- Create: `internal/crypto/x25519_box_test.go`

- [ ] **Step 1: 写失败测试（4 个 case）**

```go
// internal/crypto/x25519_box_test.go
package crypto

import "testing"

func TestX25519Box_SealOpen_Roundtrip(t *testing.T) {
    senderPriv, senderPub, _ := GenerateX25519()
    receiverPriv, receiverPub, _ := GenerateX25519()
    plain := []byte("secret tasks")
    ciphertext, nonce, err := SealBox(plain, receiverPub, senderPriv)
    if err != nil {
        t.Fatalf("seal: %v", err)
    }
    recovered, err := OpenBox(ciphertext, nonce, senderPub, receiverPriv)
    if err != nil {
        t.Fatalf("open: %v", err)
    }
    if string(recovered) != string(plain) {
        t.Fatalf("plaintext mismatch: got %q, want %q", recovered, plain)
    }
}

func TestX25519Box_Open_WrongKey_Fails(t *testing.T) {
    senderPriv, _, _ := GenerateX25519()
    _, receiverPub, _ := GenerateX25519()
    wrongPriv, _, _ := GenerateX25519()
    ciphertext, nonce, _ := SealBox([]byte("x"), receiverPub, senderPriv)
    if _, err := OpenBox(ciphertext, nonce, receiverPub, wrongPriv); err == nil {
        t.Fatal("open should have failed with wrong key")
    }
}

func TestX25519Box_Open_TamperedCiphertext_Fails(t *testing.T) {
    senderPriv, senderPub, _ := GenerateX25519()
    receiverPriv, receiverPub, _ := GenerateX25519()
    ciphertext, nonce, _ := SealBox([]byte("x"), receiverPub, senderPriv)
    ciphertext[0] ^= 0xFF
    if _, err := OpenBox(ciphertext, nonce, senderPub, receiverPriv); err == nil {
        t.Fatal("open should have failed with tampered ciphertext (Poly1305 MAC)")
    }
}

func TestX25519_KeySize(t *testing.T) {
    priv, pub, _ := GenerateX25519()
    if len(priv) != 32 || len(pub) != 32 {
        t.Fatalf("X25519 keys must be 32 bytes; got priv=%d pub=%d", len(priv), len(pub))
    }
}
```

- [ ] **Step 2: 跑测验证 FAIL**

```bash
go test ./internal/crypto/ -run TestX25519 -v
# Expected: FAIL
```

- [ ] **Step 3: 实现**

```go
// internal/crypto/x25519_box.go
package crypto

import (
    "crypto/rand"
    "errors"
    "golang.org/x/crypto/nacl/box"
)

type X25519PrivateKey [32]byte
type X25519PublicKey  [32]byte
type Nonce            [24]byte

func GenerateX25519() (X25519PrivateKey, X25519PublicKey, error) {
    pubPtr, privPtr, err := box.GenerateKey(rand.Reader)
    if err != nil {
        return X25519PrivateKey{}, X25519PublicKey{}, err
    }
    return X25519PrivateKey(*privPtr), X25519PublicKey(*pubPtr), nil
}

// SealBox encrypts plain for recipient using sender's priv key.
// Returns (ciphertext, nonce, err).
func SealBox(plain []byte, recipientPub X25519PublicKey, senderPriv X25519PrivateKey) ([]byte, Nonce, error) {
    var nonce Nonce
    if _, err := rand.Read(nonce[:]); err != nil {
        return nil, Nonce{}, err
    }
    pub := [32]byte(recipientPub)
    priv := [32]byte(senderPriv)
    ciphertext := box.Seal(nil, plain, (*[24]byte)(&nonce), &pub, &priv)
    return ciphertext, nonce, nil
}

func OpenBox(ciphertext []byte, nonce Nonce, senderPub X25519PublicKey, recipientPriv X25519PrivateKey) ([]byte, error) {
    pub := [32]byte(senderPub)
    priv := [32]byte(recipientPriv)
    plain, ok := box.Open(nil, ciphertext, (*[24]byte)(&nonce), &pub, &priv)
    if !ok {
        return nil, errors.New("nacl/box: decryption failed (auth tag mismatch)")
    }
    return plain, nil
}
```

- [ ] **Step 4: 跑测验证 PASS**

```bash
go test ./internal/crypto/ -run TestX25519 -v
# Expected: PASS（4 个测试）
```

- [ ] **Step 5: Commit**

```bash
git add internal/crypto/x25519_box.go internal/crypto/x25519_box_test.go
git commit -m "执行：crypto - X25519 + NaCl Box 加 / 解密"
```

#### Task A.1.3: Keypair 持久化（生成 / 写盘 / 加载 / 权限）

**Files:**
- Create: `internal/crypto/keys.go`
- Create: `internal/crypto/keys_test.go`

- [ ] **Step 1: 写失败测试**

```go
// internal/crypto/keys_test.go
package crypto

import (
    "os"
    "path/filepath"
    "testing"
)

func TestSaveLoadEd25519(t *testing.T) {
    dir := t.TempDir()
    path := filepath.Join(dir, "sign.key")
    priv, pub, _ := GenerateEd25519()

    if err := SaveEd25519PrivateKey(path, priv); err != nil {
        t.Fatalf("save: %v", err)
    }
    info, _ := os.Stat(path)
    if info.Mode().Perm() != 0600 {
        t.Errorf("file perm = %o; want 0600", info.Mode().Perm())
    }

    loaded, err := LoadEd25519PrivateKey(path)
    if err != nil {
        t.Fatalf("load: %v", err)
    }
    sig := SignEd25519(loaded, []byte("x"))
    if !VerifyEd25519(pub, []byte("x"), sig) {
        t.Fatal("loaded key produces invalid signature")
    }
}

func TestSaveLoadX25519(t *testing.T) {
    dir := t.TempDir()
    path := filepath.Join(dir, "enc.key")
    priv, _, _ := GenerateX25519()

    if err := SaveX25519PrivateKey(path, priv); err != nil {
        t.Fatalf("save: %v", err)
    }
    loaded, err := LoadX25519PrivateKey(path)
    if err != nil {
        t.Fatalf("load: %v", err)
    }
    if loaded != priv {
        t.Fatal("loaded X25519 key differs")
    }
}
```

- [ ] **Step 2-5: 实现 + 验测 + commit**

实现 `SaveEd25519PrivateKey` / `LoadEd25519PrivateKey` / `SaveX25519PrivateKey` / `LoadX25519PrivateKey`：
- 写盘时 `os.WriteFile(path, key, 0600)`
- 读盘时 `os.ReadFile` + size validation (Ed25519=64B priv, X25519=32B)
- 文件存在但权限非 0600 → 报错（防误配置）

Commit message: `执行：crypto - 密钥持久化（chmod 0600）`

### A.2 State 持久层 (W1)

#### Task A.2.1: SQLite executed_packs 黑名单

**Files:**
- Create: `internal/state/sqlite.go`
- Create: `internal/state/sqlite_test.go`

- [ ] **Step 1: 写失败测试**

```go
package state

import (
    "path/filepath"
    "testing"
)

func TestBlacklist_AddAndCheck(t *testing.T) {
    db, err := OpenSQLite(filepath.Join(t.TempDir(), "test.db"))
    if err != nil {
        t.Fatal(err)
    }
    defer db.Close()

    if err := db.AddExecutedPack("pack-1"); err != nil {
        t.Fatal(err)
    }
    ok, _ := db.HasExecutedPack("pack-1")
    if !ok {
        t.Fatal("pack-1 should be in blacklist")
    }
    ok, _ = db.HasExecutedPack("pack-2")
    if ok {
        t.Fatal("pack-2 should NOT be in blacklist")
    }
}

func TestBlacklist_IdempotentAdd(t *testing.T) {
    db, _ := OpenSQLite(filepath.Join(t.TempDir(), "test.db"))
    defer db.Close()
    _ = db.AddExecutedPack("pack-1")
    if err := db.AddExecutedPack("pack-1"); err != nil {
        t.Fatalf("re-add should be idempotent, got: %v", err)
    }
}
```

- [ ] **Step 2-5：实现 SQLite schema + Add/Has + commit**

```go
// internal/state/sqlite.go
package state

import (
    "database/sql"
    "time"
    _ "modernc.org/sqlite"
)

type SQLite struct{ db *sql.DB }

func OpenSQLite(path string) (*SQLite, error) {
    db, err := sql.Open("sqlite", path)
    if err != nil {
        return nil, err
    }
    if _, err := db.Exec(`
        CREATE TABLE IF NOT EXISTS executed_packs (
            pack_id TEXT PRIMARY KEY,
            executed_at TIMESTAMP NOT NULL
        );
    `); err != nil {
        return nil, err
    }
    return &SQLite{db: db}, nil
}

func (s *SQLite) Close() error { return s.db.Close() }

func (s *SQLite) AddExecutedPack(packID string) error {
    _, err := s.db.Exec(`INSERT OR IGNORE INTO executed_packs(pack_id, executed_at) VALUES (?, ?)`,
        packID, time.Now().UTC())
    return err
}

func (s *SQLite) HasExecutedPack(packID string) (bool, error) {
    var found int
    err := s.db.QueryRow(`SELECT 1 FROM executed_packs WHERE pack_id = ?`, packID).Scan(&found)
    if err == sql.ErrNoRows {
        return false, nil
    }
    return err == nil, err
}
```

Commit message: `执行：state - SQLite executed_packs 黑名单`

### A.3 Onboarding 流程 (W1-W2)

#### Task A.3.1: install_pack 数据结构 + 解析

**Files:**
- Create: `internal/onboard/install_pack.go`
- Create: `internal/onboard/install_pack_test.go`
- Create: `testdata/install_pack_valid.json`

- [ ] **Step 1: 准备测试 fixture**

```json
// testdata/install_pack_valid.json
{
  "agent_id": "8a7b9c1d-2e3f-4a5b-9c8d-1f2e3a4b5c6d",
  "onboard_token": "tok_AbCdEf123456",
  "platform_ed25519_pub_b64": "MCowBQYDK2VwAyEAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx=",
  "platform_x25519_pub_b64": "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy=",
  "platform_url": "https://veriguard.example.com",
  "platform_cert_fingerprint_sha256": "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
  "ttl_seconds": 86400
}
```

- [ ] **Step 2: 写测试**

```go
// internal/onboard/install_pack_test.go
package onboard

import (
    "os"
    "path/filepath"
    "testing"
)

func TestLoadInstallPack_Valid(t *testing.T) {
    data, _ := os.ReadFile(filepath.Join("..", "..", "testdata", "install_pack_valid.json"))
    p, err := ParseInstallPack(data)
    if err != nil {
        t.Fatalf("parse: %v", err)
    }
    if p.AgentID != "8a7b9c1d-2e3f-4a5b-9c8d-1f2e3a4b5c6d" {
        t.Errorf("agent_id mismatch: %s", p.AgentID)
    }
    if p.PlatformURL != "https://veriguard.example.com" {
        t.Errorf("platform_url mismatch")
    }
    if p.PlatformCertFingerprintSHA256 == "" {
        t.Error("cert fingerprint empty")
    }
}

func TestLoadInstallPack_MissingField_Fails(t *testing.T) {
    if _, err := ParseInstallPack([]byte(`{}`)); err == nil {
        t.Fatal("should fail on empty pack")
    }
}
```

- [ ] **Step 3-5: 实现 InstallPack struct + 解析 + 必填校验 + commit**

```go
// internal/onboard/install_pack.go
package onboard

import (
    "encoding/json"
    "fmt"
)

type InstallPack struct {
    AgentID                       string `json:"agent_id"`
    OnboardToken                  string `json:"onboard_token"`
    PlatformEd25519PubB64         string `json:"platform_ed25519_pub_b64"`
    PlatformX25519PubB64          string `json:"platform_x25519_pub_b64"`
    PlatformURL                   string `json:"platform_url"`
    PlatformCertFingerprintSHA256 string `json:"platform_cert_fingerprint_sha256"`
    TTLSeconds                    int    `json:"ttl_seconds"`
}

func ParseInstallPack(data []byte) (*InstallPack, error) {
    var p InstallPack
    if err := json.Unmarshal(data, &p); err != nil {
        return nil, fmt.Errorf("install_pack json: %w", err)
    }
    if p.AgentID == "" || p.OnboardToken == "" || p.PlatformURL == "" ||
        p.PlatformEd25519PubB64 == "" || p.PlatformX25519PubB64 == "" {
        return nil, fmt.Errorf("install_pack missing required field")
    }
    return &p, nil
}
```

#### Task A.3.2: `veriguard-agent init --install-pack` 命令

**Files:**
- Create: `internal/onboard/init.go`
- Create: `cmd/veriguard-agent/main.go`（含 cobra）

实现：读 install_pack 文件 → 生成本机 4 把 keypair → 落 `<state-dir>/keys/{sign,enc}.{priv,pub}.key` → 写 `<state-dir>/install_pack.json` 副本（含 platform pubkey）→ 调 Mode A 注册（默认）/ 输出 .vregister 文件（`--register-offline-output`）。

详细测试：mock platform HTTP server 接收 register POST → 校验 body 含 A_sign_pub / A_enc_pub / capabilities / onboard_token。

Commit: `执行：onboard - init 命令 + 4 keypair 生成 + Mode A 注册`

#### Task A.3.3: `--bootstrap` 一行 curl 入口

**Files:**
- Create: `internal/onboard/bootstrap.go`
- Create: `internal/onboard/bootstrap_test.go`

逻辑：
1. `--bootstrap` flag 触发：拿 `--onboard-token` + `--platform-url` + `--platform-cert-pin`
2. POST `<platform-url>/api/agent/onboard/bootstrap` body=`{onboard_token}`，cert pin 校验
3. 收到 install_pack JSON → 落盘 `<state-dir>/install_pack.json`
4. 调用 `init.go` 的标准 init 流程

测试：mock platform 提供 bootstrap endpoint → agent bootstrap → 验最终 state dir 含 keys + register 完成

Commit: `执行：onboard - bootstrap 一行 curl 入口`

#### Task A.3.4: TLS cert pin 校验

**Files:**
- Modify: `internal/transport/proxy.go` (新增 cert pin verifier)
- Create: `internal/crypto/cert_pin.go`
- Create: `internal/crypto/cert_pin_test.go`

实现 `http.Transport.TLSClientConfig.VerifyPeerCertificate` callback：算 leaf cert SHA256 → 与 pin 比对 → 不符 reject。

Commit: `执行：crypto - TLS 证书指纹 pin 校验`

### A.4 Mode A Transport (W2)

#### Task A.4.1: 请求签名 (X-Veriguard-Signature)

**Files:**
- Create: `internal/transport/sign.go`
- Create: `internal/transport/sign_test.go`

逻辑：
- 对 GET 请求：签 `<method> <path>?<query>\n<X-Veriguard-Timestamp>` (RFC3339 UTC)
- 对 POST 请求：签 `<method> <path>\n<X-Veriguard-Timestamp>\n<sha256(body)>`
- header `X-Veriguard-Signature: <base64 ed25519 sig>`
- header `X-Veriguard-Timestamp: <RFC3339>`
- 平台侧 (Java) 用同样规则验

Test: roundtrip 自验。

Commit: `执行：transport - X-Veriguard-Signature 请求签名`

#### Task A.4.2: HTTPS poll 主循环

**Files:**
- Create: `internal/transport/poll.go`
- Create: `internal/transport/poll_test.go`

```go
// 核心结构
type Poller struct {
    PlatformURL  string
    AgentID      string
    Capabilities []string
    SignPriv     crypto.Ed25519PrivateKey
    HTTPClient   *http.Client
    PollInterval time.Duration  // 默认 5s
}

func (p *Poller) Run(ctx context.Context, dispatcher TaskDispatcher) error {
    for {
        select {
        case <-ctx.Done():
            return ctx.Err()
        default:
        }
        tasks, err := p.fetchTasks(ctx)
        if err != nil {
            // exponential backoff: 5s→10s→20s→...→max 5min
            p.backoff()
            continue
        }
        for _, t := range tasks {
            result := dispatcher.Execute(ctx, t)
            _ = p.postResult(ctx, t.TaskID, result)
        }
        time.Sleep(p.PollInterval)
    }
}
```

Test: mock HTTPS server，poll 拿 tasks → 调 dispatcher mock → post results 回。

Commit: `执行：transport - Mode A HTTPS poll 主循环`

#### Task A.4.3: HTTPS_PROXY env 支持

**Files:**
- Modify: `internal/transport/proxy.go`

逻辑：Go `http.Transport.Proxy = http.ProxyFromEnvironment`（stdlib 默认行为，但显式设置以确保）。

Test: 设 `HTTPS_PROXY=http://localhost:3128` env → agent client 走代理。

Commit: `执行：transport - HTTPS_PROXY 环境变量支持`

### A.5 Capabilities (W2)

#### Task A.5.1: Capability registry

**Files:**
- Create: `internal/capabilities/registry.go`

```go
type Capability interface {
    Name() string
    Execute(ctx context.Context, task Task) Result
}

type Registry struct{ caps map[string]Capability }

func NewRegistry() *Registry { return &Registry{caps: map[string]Capability{}} }
func (r *Registry) Register(c Capability) { r.caps[c.Name()] = c }
func (r *Registry) Get(name string) (Capability, bool) {
    c, ok := r.caps[name]
    return c, ok
}
func (r *Registry) Names() []string {
    out := make([]string, 0, len(r.caps))
    for name := range r.caps { out = append(out, name) }
    sort.Strings(out)
    return out
}
```

Test: register 3 个 mock capability → list names 排序输出。

Commit: `执行：capabilities - registry 注册中心`

#### Task A.5.2: http_attack capability (§3 边界)

**Files:**
- Create: `internal/capabilities/http_attack.go`
- Create: `internal/capabilities/http_attack_test.go`

逻辑：解 task.payload → 构造 `http.Request`（method/url/headers/body）→ 发送 → 比 response 与 expected_status_codes / expected_body_regex → 写 Result。

Test: mock target server → http_attack 发 SQLi 模拟 payload → 验 response_digest 完整。

Commit: `执行：capabilities - http_attack (§3 边界场景)`

#### Task A.5.3: pcap_replay capability (§4 流量)

**Files:**
- Create: `internal/capabilities/pcap_replay.go`
- Create: `internal/capabilities/pcap_replay_test.go`

逻辑：解 task.payload.pcap_url → 下载 .pcap 文件到临时目录 → `tcpreplay --intf1=<iface> /tmp/x.pcap` 子进程 → exit_code 回写。

依赖：靶机预装 tcpreplay；docker 容器或部署文档加 apt note。

Test: mock pcap 文件 + mock tcpreplay binary 验调用参数。

Commit: `执行：capabilities - pcap_replay (§4 流量场景)`

#### Task A.5.4: implant_drop capability (§5 主机)

**Files:**
- Create: `internal/capabilities/implant_drop.go`
- Create: `internal/capabilities/implant_drop_test.go`

逻辑：下载 implant binary → chmod +x → 通过 `internal/implant/manager.go` 启动 implant 子进程并读 NDJSON pipe → 收集 final result。

详见 Task A.6.

Commit: `执行：capabilities - implant_drop`

#### Task A.5.5: command_inject capability (§5 主机)

**Files:**
- Create: `internal/capabilities/command_inject.go`

逻辑：基本同 implant_drop，但 task.payload 是 Command 而非任意 binary；payload spec 含 executor (`bash`/`powershell`) + content (cmd 字符串)。

实质：command_inject = implant_drop 但限定 payload type=Command。可考虑合并为同一个 capability，由 task.payload.payload_type 字段决定具体 implant payload 执行。

Commit: `执行：capabilities - command_inject`

### A.6 Implant Manager (W2)

#### Task A.6.1: Named pipe 跨平台

**Files:**
- Create: `internal/implant/pipe.go`（跨平台 wrapper）
- Create: `internal/implant/pipe_unix.go`（//go:build unix；mkfifo）
- Create: `internal/implant/pipe_windows.go`（//go:build windows；CreateNamedPipeW）

接口：
```go
type Pipe interface {
    Path() string
    OpenRead() (io.ReadCloser, error)
    Cleanup() error
}

func NewTempPipe(name string) (Pipe, error)
```

Test：建 pipe → 子进程 echo 写一行 → reader 读到。

Commit: `执行：implant - named pipe 跨平台 wrapper`

#### Task A.6.2: Implant 下载 + 启动 + NDJSON 读取

**Files:**
- Create: `internal/implant/manager.go`
- Create: `internal/implant/manager_test.go`

逻辑：
1. `GET /api/agent/implant/download/<os>/<arch>` 拉 binary
2. 校 SHA256 header
3. 落盘 `<state-dir>/implants/veriguard-implant-<hash>` chmod 0700
4. 建 named pipe
5. `exec.Cmd(implantPath, --task-id ... --result-pipe ... --self-delete)` 启动
6. `bufio.Scanner` 逐行解 NDJSON 直到 `event_type == "result_final"`
7. 收 final result → return Result

Test：放个 mock implant binary（shell 脚本）写 NDJSON → manager 解出 result。

Commit: `执行：implant - manager 下载 + 启动 + NDJSON 收果`

### A.7 Mode C - `.vpack` 解析 + 执行 (W3, security-critical)

#### Task A.7.1: `.vpack` 解析 + 校签 + 解密

**Files:**
- Create: `internal/pack/vpack.go`
- Create: `internal/pack/vpack_test.go`
- Create: `testdata/pack_valid.vpack` 等 fixture（W1 Java 端配合生成或纯 Go 自造）

```go
type VPack struct {
    SchemaVersion     string
    Format            string                 // "vpack"
    MetadataPlaintext PackMetadata
    EnvelopeEncrypted EnvelopeEncrypted
    Signature         Signature
}

type PackMetadata struct {
    PackID                string `json:"pack_id"`
    PlatformID            string `json:"platform_id"`
    AgentID               string `json:"agent_id"`
    IssuedAt              string `json:"issued_at"`
    TaskCount             int    `json:"task_count"`
    SchemaVersionPayload  string `json:"schema_version_payload"`
    ExportedBy            string `json:"exported_by"`
}

// LoadAndVerify implements the 9-step decision tree from spec §3.5.5.
func LoadAndVerify(
    path string,
    expectedAgentID, expectedPlatformID string,
    agentEncPriv crypto.X25519PrivateKey,
    platformSignPub crypto.Ed25519PublicKey,
    blacklist *state.SQLite,
) (*VPack, []byte /* decrypted tasks JSON */, error) {
    // 1. file load + JSON parse
    // 2. signature verify (Ed25519)
    // 3. platform_id check
    // 4. agent_id check
    // 5. pack_id NOT in blacklist
    // 6. task_count <= 1000
    // 7. envelope decrypt (nacl/box)
    // 8. decrypted tasks count == metadata.task_count
    // 返回 (vpack, decrypted_tasks_json, nil) 或 (nil, nil, err)
}
```

Test：12 个安全测试 case（T-1 到 T-7 + 边界）：
- valid vpack → 解开成功
- 改 1 字节 ciphertext → MAC fail
- 改 metadata.agent_id → signature fail
- 错 platform_sign_pub → fail
- 错 agent_enc_priv → decrypt fail
- pack_id 在黑名单 → reject
- task_count = 1001 → reject
- metadata.task_count != actual decrypted count → reject

Commit: `执行：pack - .vpack 加载 + 9 步校验决策树`

#### Task A.7.2: `.vresults` 签 + 加密 + 写盘

**Files:**
- Create: `internal/pack/vresults.go`
- Create: `internal/pack/vresults_test.go`

对称结构：建 metadata + encrypt results JSON with platform X25519 pub + sign with agent Ed25519 priv → JSON envelope 写文件。

Test：写 → reader-side（mock platform 侧解开）验内容一致。

Commit: `执行：pack - .vresults 签名 + 加密 + 写盘`

#### Task A.7.3: 单包 Mode C 执行

**Files:**
- Create: `internal/pack/single.go`
- Create: `internal/pack/single_test.go`

逻辑：load .vpack → 遍 tasks → 调 capabilities 执行 → 累 results → 写 .vresults → 加 pack_id 到黑名单。

Commit: `执行：pack - Mode C 单包执行流程`

#### Task A.7.4: 多包目录扫描串行

**Files:**
- Create: `internal/pack/multi.go`
- Create: `internal/pack/multi_test.go`

逻辑：扫目录 → lex 排序 → 串行执行；某包签名错则 abort 整个 batch；记录哪些包已执行。

Commit: `执行：pack - Mode C 多包目录串行执行`

### A.8 Service Install + CI (W3)

#### Task A.8.1: systemd unit (Linux)
**Files:** `internal/service/systemd_linux.go` —— 生成 `/etc/systemd/system/veriguard-agent.service` 写文件 + `systemctl enable/start` 调用。

#### Task A.8.2: launchd plist (macOS)
**Files:** `internal/service/launchd_darwin.go` —— 生成 `/Library/LaunchDaemons/io.veriguard.agent.plist`。

#### Task A.8.3: Windows service
**Files:** `internal/service/windows.go` —— 用 `golang.org/x/sys/windows/svc` 注册服务。

#### Task A.8.4: uninstall 命令
对应 service 反向操作。

#### Task A.8.5: rotate-keys CLI
**Files:** `internal/onboard/rotate.go` —— 生成新 keypair → POST `/api/agent/keys/rotate` → 收到 ack 后用新 key 替换老 key（fs 原子换名）。

#### Task A.8.6: GitHub Actions CI matrix

**Files:** `.github/workflows/build-release.yml`

```yaml
name: Build Release Binaries
on:
  push:
    tags: ['v*']
jobs:
  build:
    strategy:
      matrix:
        include:
          - { os: ubuntu-latest, goos: linux,   goarch: amd64, ext: ''    }
          - { os: ubuntu-latest, goos: linux,   goarch: arm64, ext: ''    }
          - { os: macos-latest,  goos: darwin,  goarch: amd64, ext: ''    }
          - { os: macos-latest,  goos: darwin,  goarch: arm64, ext: ''    }
          - { os: windows-latest, goos: windows, goarch: amd64, ext: '.exe' }
          - { os: windows-latest, goos: windows, goarch: arm64, ext: '.exe' }
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with: { go-version: '1.22' }
      - run: GOOS=${{matrix.goos}} GOARCH=${{matrix.goarch}} go build -o veriguard-agent${{matrix.ext}} ./cmd/veriguard-agent
      - run: sha256sum veriguard-agent${{matrix.ext}} > veriguard-agent-${{matrix.goos}}-${{matrix.goarch}}.sha256
      - uses: softprops/action-gh-release@v2
        with:
          files: |
            veriguard-agent${{matrix.ext}}
            veriguard-agent-${{matrix.goos}}-${{matrix.goarch}}.sha256
```

Commit: `执行：CI - matrix build + release pipeline (6 binaries)`

---

## 6. Phase B: Stream B — veriguard-implant (W1, parallel)

### Task B.1: CLI parser + main

**Files:** `cmd/veriguard-implant/main.go`

```go
package main

import (
    "flag"
    "os"
    // ...
)

func main() {
    taskID := flag.String("task-id", "", "required")
    payloadType := flag.String("payload-type", "", "required: Command|Executable|FileDrop|DnsResolution|NetworkTraffic")
    payloadB64 := flag.String("payload-b64", "", "required: base64 of payload JSON")
    resultPipe := flag.String("result-pipe", "", "required: named pipe path")
    timeout := flag.Duration("timeout", 60*time.Second, "")
    selfDelete := flag.Bool("self-delete", false, "")
    flag.Parse()

    if *taskID == "" || *payloadType == "" || *payloadB64 == "" || *resultPipe == "" {
        os.Exit(2) // payload parsing failure
    }
    // ... dispatch to payload handler
    if *selfDelete {
        cleanup.SelfDelete()
    }
    os.Exit(0)
}
```

Test：argument parsing + early exit code 2 on missing args。

Commit: `执行：implant - CLI parser + main + exit code 契约`

### Task B.2: NDJSON Result Writer

**Files:** `internal/result/writer.go`

```go
type Writer struct{ f io.WriteCloser }

func NewWriter(pipePath string) (*Writer, error) {
    f, err := os.OpenFile(pipePath, os.O_WRONLY, 0)
    if err != nil { return nil, err }
    return &Writer{f: f}, nil
}

func (w *Writer) WriteEvent(event Event) error {
    line, _ := json.Marshal(event)
    line = append(line, '\n')
    _, err := w.f.Write(line)
    return err
}

func (w *Writer) Close() error { return w.f.Close() }

// Event types per spec § 3.3.3
type Event struct {
    EventType string `json:"event_type"`
    TaskID    string `json:"task_id"`
    // ... unioned fields per type
}
```

Test：写 4 类 event → reader 端 bufio.Scanner 逐行解出。

Commit: `执行：result - NDJSON writer (spec § 3.3.3 契约)`

### Task B.3: Command payload

**Files:** `internal/payload/command.go`

逻辑：解 payload.content → exec shell → 收 stdout/stderr/exit_code → 写 result_final。

Test：执行 `echo test` → 验 stdout="test\n"。

Commit: `执行：payload - Command (执行 ART 1781 条用例)`

### Task B.4-B.7: Executable / FileDrop / DnsResolution / NetworkTraffic payloads

每个一个文件 + 单测；逻辑参 spec § 3.3.2 + spec § 4.2 12 类映射表。

Commits:
- `执行：payload - Executable (drop + execve)`
- `执行：payload - FileDrop (落盘 + 权限设置)`
- `执行：payload - DnsResolution (DNS 查询)`
- `执行：payload - NetworkTraffic (自定义 TCP/UDP/ICMP)`

### Task B.8: Self-delete

**Files:** `internal/cleanup/self_delete_{linux,darwin,windows}.go`

Linux/macOS：`os.Remove(os.Args[0])`（POSIX 允许文件被 unlink 但进程仍能跑完）
Windows：用 `MoveFileExW(path, NULL, MOVEFILE_DELAY_UNTIL_REBOOT)` 或下次启动删；本进程退出前不能删自己。

Commit: `执行：cleanup - 跨平台 self-delete`

### Task B.9: CI

参 Task A.8.6，repo 内 `.github/workflows/build-release.yml`。

Commit: `执行：CI - veriguard-implant matrix build`

---

## 7. Phase C: Stream C — veriguard-api Java 改造 (W2-W3, parallel)

### Task C.1: V19 + V20 Flyway migrations

**Files:**
- Create: `veriguard-api/src/main/resources/db/migration/V19__agent_crypto_keys.sql`
- Create: `veriguard-api/src/main/resources/db/migration/V20__offline_pack_audit.sql`

内容参 spec § 3.6（已含完整 SQL）。

测试：
```bash
cd veriguard-api
mvn flyway:migrate
docker exec veriguard-dev-pgsql psql -U veriguard -d veriguard -c "\d agents"
docker exec veriguard-dev-pgsql psql -U veriguard -d veriguard -c "\d offline_pack_audit"
```

Commit: `执行：V19+V20 - agents 加密列 + offline_pack_audit 表`

### Task C.2: Crypto Services (Java)

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/crypto/Ed25519SignatureService.java`
- Create: `veriguard-api/src/main/java/io/veriguard/crypto/X25519BoxService.java`
- Create: `veriguard-api/src/test/java/io/veriguard/crypto/Ed25519SignatureServiceTest.java`
- Create: `veriguard-api/src/test/java/io/veriguard/crypto/X25519BoxServiceTest.java`
- Modify: `pom.xml` 加 BouncyCastle `bcprov-jdk18on:1.78.1`

```java
// Ed25519SignatureService.java
@Service
public class Ed25519SignatureService {
    public byte[] sign(byte[] privKey, byte[] msg) { /* BC impl */ }
    public boolean verify(byte[] pubKey, byte[] msg, byte[] sig) { /* BC impl */ }
    public KeyPair generate() { /* */ }
}
```

测试：跨语言互测——用 Go 端 fixture 生成 sign，Java 端验签通过。

Commit: `执行：crypto - Java Ed25519 + X25519 服务 (BouncyCastle)`

### Task C.3: VpackSerializer + VresultsSerializer

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/crypto/VpackSerializer.java`
- Create: `veriguard-api/src/main/java/io/veriguard/crypto/VresultsSerializer.java`
- Create: `veriguard-api/src/test/java/io/veriguard/crypto/VpackSerializerTest.java`

实现：构造 `.vpack` JSON envelope（含 metadata + encrypt envelope + sign）→ 序列化；解析倒序。

测试：与 Go 端 fixture 互通：Java 端 build .vpack → Go 端 LoadAndVerify 通过；反之亦然。

Commit: `执行：crypto - VpackSerializer + VresultsSerializer + Go/Java 互操作`

### Task C.4: AgentOnboardApi (init/register/bootstrap)

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/rest/agent/AgentOnboardApi.java`
- Create: `veriguard-api/src/test/java/io/veriguard/rest/agent/AgentOnboardApiTest.java`

3 个 endpoint，body/resp 见 spec § 3.5.1。

测试：mockMvc + 3 endpoint 各 happy path + 失败 case（onboard_token 已用、过期、签名错）。

Commit: `执行：rest/agent - AgentOnboardApi (init+register+bootstrap)`

### Task C.5: AgentTaskQueueApi (poll/result)

**Files:** `veriguard-api/src/main/java/io/veriguard/rest/agent/AgentTaskQueueApi.java`

实现：
- GET /api/agent/poll：验 X-Veriguard-Signature → 查待分配 tasks → 返回
- POST /api/agent/task/<id>/result：验签 → 更新 inject_execution 状态 → audit

Commit: `执行：rest/agent - AgentTaskQueueApi (poll + result)`

### Task C.6: AgentOfflinePackApi (export/import)

**Files:** `veriguard-api/src/main/java/io/veriguard/rest/agent/AgentOfflinePackApi.java`

实现：
- POST /api/agent/offline-pack/export：拼 tasks → 调 VpackSerializer → 返回 .vpack JSON
- POST /api/agent/offline-pack/import：multipart 接 .vresults → 调 VresultsSerializer 解 → 入库

Commit: `执行：rest/agent - AgentOfflinePackApi (export + import)`

### Task C.7: AgentImplantDownloadApi

**Files:** `veriguard-api/src/main/java/io/veriguard/rest/agent/AgentImplantDownloadApi.java`

实现：从 `classpath:agents/veriguard-implant/<os>/<arch>/veriguard-implant[.exe]` 读取并 stream octet-stream + X-SHA256 header。

Commit: `执行：rest/agent - AgentImplantDownloadApi (二进制下载)`

### Task C.8: AgentInstallScriptApi (一行 curl)

**Files:** `veriguard-api/src/main/java/io/veriguard/rest/agent/AgentInstallScriptApi.java`

实现：
- GET /install/<token>：模板渲染 bash 脚本（参 spec § Q2 详解），token 内嵌于脚本
- GET /install/<token>/ps1：PowerShell 等价物
- GET /install/binary/<os>/<arch>/veriguard-agent[.exe]：octet-stream

测试：模拟请求验脚本内容含 token 占位、SHA256 校验、OS 探测代码。

Commit: `执行：rest/agent - AgentInstallScriptApi (一行 curl Tailscale 风格)`

### Task C.9: WebAttackDispatchService 真接通

**Files:** `veriguard-api/src/main/java/io/veriguard/injectors/web_attack/service/WebAttackDispatchService.java`

修改：去掉"不真发"TODO；新增 `dispatch(Execution, Agent, WebAttackContent)` 方法把 task 写到 agent_task 队列（或扩既有 inject_executions）+ 异步等回执。

测试：单测 mock agent + verify task 进队列；集成测真 agent 拉到任务并回报。

Commit: `执行：WebAttackDispatchService 真接通 dispatch 通道`

### Task C.10: HttpInjectExecutor (§3.6) 真接通

**Files:** `veriguard-api/src/main/java/io/veriguard/combination/executor/HttpInjectExecutor.java`

修改：去掉 `throw UnsupportedOperationException`；调 WebAttackDispatchService.dispatch()；返回 AttackCombinationHitState by agent 回执。

Commit: `执行：HttpInjectExecutor §3.6 ★2 真接通 30k 组合实跑`

### Task C.11: CommandInjectExecutor (新)

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/injectors/command_inject/CommandInjectContract.java`
- Create: `veriguard-api/src/main/java/io/veriguard/injectors/command_inject/CommandInjectExecutor.java`
- Create: `veriguard-api/src/main/java/io/veriguard/injectors/command_inject/model/CommandInjectContent.java`
- Create: `veriguard-api/src/main/java/io/veriguard/injectors/command_inject/service/CommandInjectDispatchService.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/command_inject/CommandInjectNodeExecutorIntegration.java`

整体仿 web_attack injector 既有模板（spec 参考 B-ii PR-C / B7）；capability="command_inject"；payload type 收 5 个 implant payload type 之一。

测试：4-6 个 TDD 单测 + integration 一条 ART Command 端到端跑通。

Commit: `执行：CommandInjectExecutor + Contract + DispatchService (新 injector)`

### Task C.12: PcapReplayExecutor 真接通

类似 C.9，把骨架 dispatch 改实接通。

Commit: `执行：PcapReplayExecutor 真接通`

### Task C.13: OfflinePackAuditService

**Files:** `veriguard-api/src/main/java/io/veriguard/audit/OfflinePackAuditService.java`

实现：export 时记 `created_by` / `exported_from_ip` / `exported_ciphertext_sha256`；import 时记 `imported_at` / `imported_by` / `result_count` / `rejected_count`。

Commit: `执行：audit - OfflinePackAuditService (export/import 审计)`

### Task C.14: AgentService.selectByCapability 严校

**Files:** Modify `veriguard-api/src/main/java/io/veriguard/service/AgentService.java`

逻辑：select 时校 task.capability ∈ agent.capabilities；不符抛 `CapabilityNotSupportedException`。

Test：mock agent w/o http_attack capability → web_attack task dispatch → 拒。

Commit: `执行：AgentService - capability 严格校验`

---

## 8. Phase I: Integration (W4)

### Task I.1: Mock platform for agent dev

**Files:** `cmd/veriguard-agent/cmd/dev-mock-platform.go`（或独立 testdata/mockserver）

跑一个最小 HTTP server 模拟 platform endpoints（poll / result / onboard）；agent 可对其开发。

Commit: `测试：dev-mock-platform (Stream A 独立开发用)`

### Task I.2: 集成 Scenario A1 - Mode A 端到端

- [ ] **Step 1: docker compose 起平台 + agent**
- [ ] **Step 2: agent install_pack 注册**
- [ ] **Step 3: 平台 push 1 个 http_attack task**
- [ ] **Step 4: agent poll 拿到，调 http_attack capability，发到 mock-waf**
- [ ] **Step 5: agent post result，平台入 DB**
- [ ] **Step 6: 验 inject_execution 状态 = COMPLETED**

详细脚本走 `veriguard-dev/docker-compose.演练.yml`。

Commit: `测试：集成 Scenario A1 - Mode A 端到端 http_attack`

### Task I.3-I.7: 其他集成场景

- I.3: Scenario A2 - Mode A 多 capability (http + cmd + pcap)
- I.4: Scenario C1 - Mode C 单包端到端
- I.5: Scenario C2 - Mode C 多包目录
- I.6: Scenario C3 - Mode C 故障注入（pack 2 签名错 → 中断）
- I.7: Scenario O3 - 一行 curl 在 Ubuntu/macOS/Win PowerShell 三平台冒烟

### Task I.8: 安全测 T-1 ~ T-8

8 个独立测试，每个一段 Go test + 一段 Java mockMvc：
- T-1 篡改 ciphertext → MAC 失败
- T-2 错 platform_sign_priv 签 → 拒
- T-3 重放 pack_id → 黑名单命中
- T-4 跨 agent → agent_id 不符
- T-5 错 agent_sign_priv 签 results → 平台拒
- T-6 错 platform_id → 拒
- T-7 task_count=1001 → 拒
- T-8 越权 capability → skip task

Commit per test 或合 1 commit: `测试：安全测 T-1 ~ T-8 全过`

---

## 9. Phase C2: Docs + 演练 (W5)

### Task C2.1: 12 类映射对照表

**Files:** Create `docs/IPv6安全验证系统-§5主机12类映射对照表.md`

内容参 spec § 4.2 完整表 + 4 字段（payload_external_id_range / injector_type / capability / expected_alert_pattern_hint）。

Commit: `执行：C2 - § 5.1 12 类 ATT&CK 映射对照表`

### Task C2.2: docker-compose.演练.yml

**Files:** Create `veriguard-dev/docker-compose.演练.yml`

7 个服务（参 spec § 4.3.1）：nginx-bridge / mock-waf / mock-ids / mock-nxsoc / mock-siem / ubuntu-test / win10-test。

Commit: `执行：C2 - docker-compose.演练.yml (7 mock service)`

### Task C2.3: 真靶机演练剧本

**Files:** Create `docs/IPv6安全验证系统-真靶机演练剧本.md`

5 章节剧本（参 spec § 4.3.2 完整内容）。

跑一遍剧本验证全 5 章节可执行 → 3 份截图素材入档。

Commit: `执行：C2 - 真靶机演练剧本 5 章节 + 3 份截图素材`

### Task C2.4: 7 份部署文档

**Files:** 参 spec § 4.4 文档清单
- `docs/agent/QUICKSTART.md`
- `docs/agent/network-topology.md`
- `docs/agent/deployment-mode-a-proxy.md`
- `docs/agent/deployment-mode-c-offline.md`
- `docs/agent/install-curl-oneliner.md`
- `docs/agent/troubleshooting.md`
- `docs/agent/FUTURE-WORK.md`

Commits（每篇 1 commit 或合 1 commit）：
- `执行：C2 - 部署文档 (7 篇全)`

---

## 10. Test Strategy Matrix

| 测试层 | 工具 | 范围 | 哪里跑 |
| --- | --- | --- | --- |
| **单元测试** | Go `testing` / JUnit + Mockito | 每个文件配 `*_test.go`/`*Test.java`；覆盖率目标 ≥ 95%（crypto）/ ≥ 80%（业务）| `go test ./...` / `mvn test` |
| **集成测试** | docker-compose.演练.yml + 真 SQLite / 真 PG | Mode A / Mode C / 三场景端到端 / Onboarding 在线+离线 | CI `integration` profile + 本地 `docker compose up` |
| **安全测试** | 黑盒 fixture-based | T-1 ~ T-8（spec § 3.8）| CI `security` profile + 每次 PR |
| **跨平台冒烟** | GitHub Actions matrix | `veriguard-agent version` 在 6 个 OS/arch 跑通 + 一行 curl 三平台 | CI release pipeline 自动 |
| **性能** | benchmark + 30k 任务 batch | `.vpack` 解密 30k task < 5s；agent poll 5s 间隔不堆积 | 手动跑 + CI `perf` profile（W5）|

---

## 11. 自审与 Spec Coverage 检查

执行本 plan 前最后审：

| Spec 节 | 实现 Task | 覆盖 |
| --- | --- | --- |
| § 1.1 招标条款依赖 | I.2 / I.3 / I.4 端到端测试覆盖 §3.5 / §3.6 / §4.1 / §5.1 | ✅ |
| § 1.2 当前代码状态 | C.9-C.12 4 个 Executor 真接通直接消解骨架 / mock 状态 | ✅ |
| § 2.1 系统总览 | Phase A/B/C 全 stream 实现总览图 | ✅ |
| § 2.2 三场景路径 | A.5.2 / A.5.3 / A.5.4 / A.5.5 capabilities | ✅ |
| § 2.3 双通信模式 | A.4 (Mode A) + A.7 (Mode C) | ✅ |
| § 2.4 4 keypair | A.1 (crypto) + C.2 (Java crypto) | ✅ |
| § 2.5 仓库与产物 | Task 0.1-0.4 + A.8 / B.9 (CI) | ✅ |
| § 2.6 Upstream 偏离 | Task 0.1-0.4 README baseline 声明 | ✅ |
| § 3.2 agent 仓 | Phase A 全部 | ✅ |
| § 3.3 implant 仓 | Phase B 全部 | ✅ |
| § 3.3.3 NDJSON pipe 契约 | B.2 (writer) + A.6 (reader) | ✅ |
| § 3.4 Platform 改造 | Phase C 全部 | ✅ |
| § 3.5.1 REST endpoint | C.4-C.8 5 个 Api 模块 | ✅ |
| § 3.5.2 .vpack schema | A.7.1 (Go reader) + C.3 (Java writer) | ✅ |
| § 3.5.3 .vresults schema | A.7.2 (Go writer) + C.3 (Java reader) | ✅ |
| § 3.5.4 Task / Result | A.4-A.6 (capabilities) + C.5 (Api) | ✅ |
| § 3.5.5 9 步校验决策树 | A.7.1 12 安全测试用例 | ✅ |
| § 3.6 V19 + V20 | C.1 | ✅ |
| § 3.7 错误处理 | 各 capability + I.6 故障注入测试 | ✅ |
| § 3.8 测试策略 | § 10 Test Strategy Matrix | ✅ |
| § 3.9 C1 DoD | § 2 PR 切分 + CP-W4 验收 | ✅ |
| § 4.2 12 类映射 | C2.1 | ✅ |
| § 4.3 演练剧本 | C2.2 + C2.3 | ✅ |
| § 4.4 部署文档 | C2.4 | ✅ |
| § 5 Out-of-Scope | C2.4 中 FUTURE-WORK.md | ✅ |
| § 6 决策日志 | spec 自含，plan 不重复 | ✅ |
| § 7 风险 | CP-W1~W5 checkpoint 含风险对策 | ✅ |
| § 8.3 周里程碑 | § 3 Checkpoints 已重述 | ✅ |
| § 8.4 并行执行策略 | § 2 PR 切分 + Stream A/B/C 划分 | ✅ |

---

*Plan 完*
