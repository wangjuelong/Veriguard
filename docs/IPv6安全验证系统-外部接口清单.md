# IPv6 安全验证系统 — 对接客户现有安全体系外部接口清单

> 来源：综合 [`IPv6安全验证系统.md`](./IPv6安全验证系统.md)（招标条款）+ [`IPv6安全验证系统-GAP分析.md`](./IPv6安全验证系统-GAP分析.md)+ [`IPv6安全验证系统-业务模块Agent与数据流.md`](./IPv6安全验证系统-业务模块Agent与数据流.md)+ [`IPv6安全验证系统-甲方待澄清清单.md`](./IPv6安全验证系统-甲方待澄清清单.md)
> 整理日期：2026 年 5 月
> 范围：模拟攻击验证闭环所需的所有外部接口（出向 / 入向 / 双向）
> 用途：第 1 周与甲方对齐接口需求 + 实施期接口规范设计

---

## 0. 接口总览

| 类别 | 接口数 | 关键对接系统 |
| --- | ---: | --- |
| 1. 告警 / SOC 平台 | 3 | 蓝盾 NxSOC（主选） / SIEM 通用 syslog / 各厂商告警 API |
| 2. 主机 Agent / EDR | 6+ | OpenAEV Agent（自建）/ Tanium / CrowdStrike / SentinelOne / Cortex XDR / Caldera / 国产 EDR |
| 3. 边界 / 流量检测设备 | 4 | WAF / IPS / NTA / IDS（厂商不一） |
| 4. 沙箱后端 | 1 | wangjuelong/cape（项目方自建） |
| 5. 协作主机 Agent | 1 | 项目方自建（fork OpenAEV-Platform/agent） |
| 6. 资产 / 配置同步 | 2 | 集团 IPv6 地址管理平台 / CMDB |
| 7. 邮件服务器 | 1 | 客户 SMTP（IMAP 不在本期范围） |
| 8. 流量回放设备 | 1 | 客户既有硬件 / 协作主机 Agent 自建 |
| 9. 用户认证 | 2 | LDAP / AD / SSO（OAuth / SAML / OIDC） |
| 10. 平台监控 | 2 | Prometheus + OpenTelemetry / 日志中心 |
| **合计** | **23** | |

按方向归类：

| 方向 | 接口数 | 含义 |
| --- | ---: | --- |
| **出向**（平台 → 外部）| 12 | 派发任务 / 查询告警 / 同步资产 / 发邮件 |
| **入向**（外部 → 平台）| 6 | 告警回填 / Agent 上报 / 沙箱回报 / 资产推送 |
| **双向** | 5 | 认证 / 监控 / 配置同步 |

---

## 1. 告警 / SOC 平台接口

### 1.1 蓝盾 NxSOC 接口（**主选 / 阻塞性**）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向（平台 → NxSOC 查询告警） |
| 触发条款 | 招标 §3 / §4 / §7.5「SOC 关联告警」+ GAP §3.1 / §4.1 / §5.1 / §7.5 + 待澄清 §1.1 |
| 协议 | REST / gRPC / Kafka topic（待澄清） |
| 认证 | API Key / OAuth / mTLS（待澄清） |
| IPv6 双栈 | 必须支持（招标 §1 强制） |
| 接口能力 | 6 维查询：（a）资产 IP；（b）时间窗（起止）；（c）严重度；（d）规则 ID；（e）规则类别；（f）关键字 |
| 频率 | 攻击编排任务执行后秒级查询；常态化监控分钟级 |
| 数据回流 | 告警明细（含规则 ID / 命中字段 / 严重度 / 时间戳 / 资产）|
| 实施位置 | 新建 `nxsoc_adapter` 子模块（GAP 跨模块新增 #6）|
| 备选方案 | NxSOC 无 API → syslog 转发 + 自建 SIEM 中转网关（增加 2 周）|
| 状态 | 🔴 **阻塞性 / 第 1 周必拿到接口文档** |

### 1.2 SIEM 通用 syslog（备选）

| 项 | 内容 |
| --- | --- |
| 方向 | 入向（检测设备 → SIEM → 平台）|
| 协议 | syslog UDP/TCP（RFC 5424），可选 TLS 加密 |
| 用途 | 当 NxSOC 不提供 API 时的备选方案；或通用 SIEM（Splunk / QRadar / ELK）对接 |
| 接收端 | 平台需起 syslog 接收器（落 Elasticsearch + 6 维索引）|
| 实施位置 | 备选路径 — Phase A 启动时同步评估 |

### 1.3 各厂商检测设备告警 API（异构兼容）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向（平台 → 检测设备 API 查告警）|
| 用途 | 若 NxSOC 未汇聚某些设备告警，需直接查厂商 API |
| 厂商分类 | WAF（如绿盟 NSFOCUS / 安恒 / 启明）/ IPS（蓝盾 BDIPS）/ IDS（国基华电 GJsec-SIDS / 天眼探针）|
| 实施风险 | 异构 API 适配工作量大；优先依赖 NxSOC 汇聚 |

---

## 2. 主机 Agent / EDR 接口

### 2.1 OpenAEV Agent 自建协议

| 项 | 内容 |
| --- | --- |
| 方向 | 双向（平台 ↔ Agent，主机侧路径 A）|
| 触发条款 | 招标 §5 主机安全验证 + 业务模块文档 第 3 章路径 A |
| 协议 | HTTPS over IPv6（jakarta.mail Session 风格的 keep-alive）|
| 认证 | mTLS + Agent 注册 Token（UUID）|
| 接口能力 | 出向：派发命令 / 配置下发；入向：Agent 心跳 / 上报命令输出 / 上报告警 |
| 主机 OS 适配 | 6 平台 × 2 架构 = 12 二进制（Linux / Windows / MacOS × x86_64 / arm64）+ 信创变种（麒麟 / UOS / 鲲鹏 / 飞腾）|
| 端口 | 双向 8443（TLS）/ Agent 主动 poll |
| 实施位置 | 独立项目 `veriguard-agent`（fork `OpenAEV-Platform/agent`）|
| 状态 | 🔴 待澄清靶机部署细则后启动（待澄清 §1.3）|

### 2.2 Tanium API（路径 B 借力）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向（平台 → Tanium 投递 remote-script）|
| 协议 | REST API + OAuth 2.0 |
| 接口能力 | `POST /api/v2/sensor_packages` 投递 package；`POST /api/v2/actions` 投递 action；`GET /api/v2/action_results` 查结果 |
| 上游已支持 | OpenAEV `integration/impl/executors/tanium/` 已实现 |
| 需甲方提供 | Tanium 服务器 URL / API Token / Computer Group ID / Action Group ID |
| 配置位置 | `application.properties` 中 `executor.tanium.*` |

### 2.3 CrowdStrike Falcon API（路径 B 借力）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向（平台 → CrowdStrike Falcon RTR）|
| 协议 | REST API + OAuth 2.0（client_id / client_secret）|
| 接口能力 | Real-Time Response (RTR) `POST /real-time-response/entities/sessions/v1`；执行 `POST /real-time-response/entities/admin-command/v1` |
| 上游已支持 | OpenAEV `integration/impl/executors/crowdstrike/` 已实现 |
| 需甲方提供 | API URL（如 `https://api.us-2.crowdstrike.com`）+ client_id + client_secret + Host Group |

### 2.4 SentinelOne Remote Script API（路径 B 借力）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向 |
| 协议 | REST API + API Token |
| 接口能力 | 远程脚本执行 |
| 上游已支持 | OpenAEV `integration/impl/executors/sentinelone/` 已实现 |
| 需甲方提供 | URL / API Key / Account ID / Site ID / Group ID / Script ID（Windows + Unix）|

### 2.5 Palo Alto Cortex XDR API（路径 B 借力）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向 |
| 上游已支持 | OpenAEV `integration/impl/executors/paloaltocortex/` 已实现 |

### 2.6 Caldera REST API（路径 B 借力）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向（平台 → Caldera C2 → Sandcat agent）|
| 协议 | REST API |
| 上游已支持 | OpenAEV `integration/impl/executors/caldera/` 已实现 |

### 2.7 国产 EDR API（待客户确认）

| 项 | 内容 |
| --- | --- |
| 厂商候选 | 奇安信天擎 / 深信服 EDR / 启明星辰 / 安博通 / 青藤云安全 |
| 状态 | 🔴 需甲方提供已部署的国产 EDR 厂商清单与 API 可用性（待澄清 §1.4）|
| 实施 | 若客户已部署且 API 可用 → 类比 Tanium 适配；否则走路径 A（OpenAEV Agent 直驱）|

---

## 3. 边界 / 流量检测设备接口

### 3.1 WAF / IPS 策略导出与命中日志

| 项 | 内容 |
| --- | --- |
| 方向 | 双向（出向：策略名同步；入向：命中告警转 SIEM 后查询）|
| 触发条款 | 招标 §3.1「策略名称」+ 覆盖度验证 + 待澄清 §2.1.1 |
| 接口能力 | 策略命名同步（用于覆盖度矩阵的策略维度）|
| 厂商候选 | 绿盟 NSFOCUS WAF / 安恒明御 / 启明星辰天清 / 蓝盾 BDIPS |
| 实施 | 部分厂商提供 REST API 导出策略；多数依赖 配置文件 / Excel 导出 |
| 状态 | 🟡 需甲方提供已部署清单 + 策略导出格式（待澄清 §2.1.1）|

### 3.2 NTA / IDS 检测设备

| 项 | 内容 |
| --- | --- |
| 方向 | 入向（设备产生告警 → SIEM → 平台查询）|
| 触发条款 | 招标 §4 流量安全验证 + GAP §4.1 / §4.2 |
| 厂商候选 | 蓝盾 BDIPS / 国基华电 GJsec-SIDS / 天眼探针 |
| 配置要求 | 设备需将告警转发到 NxSOC / SIEM；告警字段含规则 ID / 资产 IP / 时间戳 |
| 状态 | 🟡 待 NxSOC 接口澄清后才能确定具体字段映射 |

### 3.3 HIDS 主机检测设备

| 项 | 内容 |
| --- | --- |
| 方向 | 入向（HIDS 产生告警 → SIEM → 平台查询）|
| 厂商候选 | 青藤云安全 / 安全狗 / 主机卫士 / 国产开源 OSSEC |
| 验证目标 | 招标 §5 主机安全验证的"被验证设备" |

---

## 4. 沙箱后端接口

### 4.1 wangjuelong/cape API（**阻塞性**）

| 项 | 内容 |
| --- | --- |
| 方向 | 双向（平台 ↔ CAPE）|
| 触发条款 | 招标 §8 + GAP §8.1 / §8.2 / §8.3 + 待澄清 §1.2 |
| 协议 | REST API（CAPE 默认）|
| 认证 | 待澄清（推断为 API Key + Basic Auth） |
| 接口能力 | （a）任务上传 `POST /tasks/create/file`；（b）状态查询 `GET /tasks/view/<id>`；（c）报告获取 `GET /tasks/report/<id>`；（d）Snapshot 回滚（自定义）；（e）网络策略下发（VM 网络配置）|
| 数据流 | 出向：样本文件 + 网络策略；入向：执行报告（行为 / 网络 / 文件落地）|
| 实施位置 | 新建 `cape_adapter`（GAP 跨模块新增 #7）|
| IPv6 支持 | 待确认 |
| 状态 | 🔴 **第 1 周对齐 cape API 版本** |

---

## 5. 协作主机 Agent 接口

### 5.1 协作主机 Agent 自建协议（PR-C / PR-D 派发已落地，客户端待开发）

| 项 | 内容 |
| --- | --- |
| 方向 | 双向（平台 ↔ 协作主机 Agent）|
| 触发条款 | 招标 §3 边界（http_attack）+ §4 流量（pcap_replay）+ 业务模块文档第 1/2 章 |
| 协议 | HTTPS over IPv6（同 OpenAEV Agent 协议风格） |
| 认证 | mTLS + Agent 注册 Token |
| 接口能力 | 出向：派发 web_attack / pcap_replay inject；入向：Agent 上报响应 / 回放结果 |
| Agent capability 声明 | `agent_capabilities` JSONB：`["http_attack", "pcap_replay"]` |
| 实施位置 | 独立项目 fork `OpenAEV-Platform/agent`，加 capability 声明 + curl/tcpreplay 子进程包装 |
| 平台侧 | PR-A / PR-C / PR-D 已完成派发逻辑（dispatch trace）|
| 状态 | 🔴 客户端代码独立子项目 / 待 §1.5 部署位置澄清后启动 |

---

## 6. 资产 / 配置同步接口

### 6.1 集团 IPv6 地址管理平台

| 项 | 内容 |
| --- | --- |
| 方向 | 入向（集团平台 → Veriguard 资产清单）|
| 触发条款 | 招标 §1.6.2 + GAP 待澄清 §1 #5 + 本清单 §1.7 |
| 协议 | 待澄清（REST / Kafka / 文件同步）|
| 频率 | 待澄清（实时 / 小时 / 日批）|
| 数据 | 主机 IPv6 + 主机名 + 业务归属 + 网段标签 |
| 实施位置 | 新建 `corp_ipv6_registry_adapter`（GAP 未列）|
| 状态 | 🔴 阻塞性，第 1-2 周拿到接口文档 |

### 6.2 CMDB（如客户有）

| 项 | 内容 |
| --- | --- |
| 方向 | 入向（CMDB → Veriguard 资产元数据）|
| 用途 | 资产业务系统映射 / 关键性评级 |
| 状态 | 🟢 一般，可后续补 |

---

## 7. 邮件服务器接口

### 7.1 SMTP（出向，PR-B 已完成对接）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向（平台 → SMTP 服务器 → 收件人邮箱）|
| 触发条款 | 招标 §6 邮件 inject + PR-B 已完成 SmtpProfile CRUD |
| 协议 | SMTP / SMTPS / SMTP+STARTTLS |
| 认证 | NONE / PASSWORD（PR-B SmtpProfile.AUTH_TYPE 已支持） |
| TLS | NONE / STARTTLS / TLS（PR-B SmtpProfile.TLS_MODE 已支持） |
| 多 Profile 支持 | ✅（PR-B 完成）每个 inject 可选不同 SMTP profile |
| 需甲方提供 | （a）测试 SMTP 服务器 IP / 端口 / 凭据；（b）发件白名单 from_alias；（c）日发送量上限；（d）是否允许使用真实业务邮件域名 |
| 状态 | 🟡 待澄清 §2.1.2（甲方配置参数） |

### 7.2 IMAP / 邮件追踪（不在本期范围）

| 项 | 内容 |
| --- | --- |
| 范围 | spec §12.1 明示边界外 |
| 后续 | 钓鱼演练用户行为追踪（追踪 pixel / 链接回调）作为独立 follow-up 子项目 |

---

## 8. 流量回放设备接口

### 8.1 客户既有流量回放设备（备选）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向（平台 → 流量回放设备 → 业务网段）|
| 触发条款 | 招标 §4 流量安全验证 + 技术方案 §1.4.2「接口化适配」 |
| 厂商候选 | Tofino / Keysight / 国产硬件回放器 |
| 接入方式 | 厂商 API / REST / 文件 + 信令触发 |
| 备选 | 协作主机 Agent + tcpreplay 自建（PR-D 已派发逻辑）|
| 状态 | 🟢 客户既有设备优先；无设备走自建 Agent |

---

## 9. 用户认证 / 权限接口

### 9.1 LDAP / Active Directory（双向）

| 项 | 内容 |
| --- | --- |
| 方向 | 双向（平台 ↔ LDAP）|
| 协议 | LDAP / LDAPS over IPv6 |
| 用途 | 用户身份验证 + 组织架构映射 |
| 上游已支持 | OpenAEV 用户模块已含 LDAP 集成（OAuth2 Resource Server pattern）|
| 需甲方提供 | LDAP 服务器 URL / Bind DN / Bind Password / 用户搜索 Base DN |

### 9.2 SSO（OAuth 2.0 / SAML / OIDC）

| 项 | 内容 |
| --- | --- |
| 方向 | 双向（浏览器 ↔ SSO IdP ↔ 平台）|
| 协议 | OAuth 2.0 / SAML 2.0 / OpenID Connect |
| 用途 | 单点登录 + 集团统一认证 |
| 上游已支持 | OpenAEV `AppSecurityConfig.java` 已实现 OAuth 2.0 + SAML |
| 需甲方提供 | IdP URL / Client ID / Client Secret / Realm / 用户映射规则 |

---

## 10. 平台监控接口

### 10.1 Prometheus + OpenTelemetry（双向，出向遥测）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向（平台 → Prometheus exporter） |
| 协议 | Prometheus pull `/actuator/prometheus`（Spring Boot Actuator 内置） |
| 数据 | 平台运行指标（请求数 / 延迟 / 错误率 / Agent 在线数 / 任务执行时长）|
| 用途 | 平台自监控 + 集成客户既有 Prometheus / Grafana |

### 10.2 日志中心（入向，集团统一日志）

| 项 | 内容 |
| --- | --- |
| 方向 | 出向（平台日志 → 集团日志中心） |
| 协议 | syslog / Filebeat / Fluent Bit |
| 用途 | 平台审计日志 + 操作日志统一归档 |
| 需甲方提供 | 日志中心接入点 + 格式约定（JSON / CEF / LEEF） |

---

## 11. 接口对接优先级（与实施 Phase 对齐）

### Phase A（前置基建，第 1-3 周）

| 优先级 | 接口 | 阻塞模块 |
| --- | --- | --- |
| 🔴 **P0** | 1.1 NxSOC 接口 | nxsoc_adapter / 4 个核心模块的告警回环 |
| 🔴 P0 | 4.1 CAPE API | cape_adapter / §8 全部沙箱工作 |
| 🔴 P0 | 6.1 集团 IPv6 资产平台 | 资产清单同步 |
| 🟡 P1 | 9.1 LDAP / AD | 用户登录 |
| 🟡 P1 | 9.2 SSO | 集团统一认证 |

### Phase B（用例 + Agent 接口，第 4-7 周）

| 优先级 | 接口 | 状态 |
| --- | --- | --- |
| 🔴 P0 | 2.1 OpenAEV Agent 协议 | 独立子项目启动 |
| 🔴 P0 | 5.1 协作主机 Agent 协议 | 独立子项目启动（fork OpenAEV-Platform/agent） |
| 🟡 P1 | 7.1 SMTP | PR-B 已完成对接，甲方配置参数 |
| 🟡 P1 | 2.2-2.6 第三方 EDR API | 上游已实现，配置 application.properties |

### Phase C（检测设备策略 + 沙箱细节，第 8-10 周）

| 优先级 | 接口 |
| --- | --- |
| 🟡 P1 | 3.1 WAF/IPS 策略导出 |
| 🟡 P1 | 3.2 NTA/IDS 告警（依赖 NxSOC 中转） |
| 🟡 P1 | 4.1 CAPE 网络策略 + Snapshot 回滚（深度集成） |
| 🟢 P2 | 2.7 国产 EDR API |

### Phase D（运维 + 监控，第 11-12 周）

| 优先级 | 接口 |
| --- | --- |
| 🟢 P2 | 10.1 Prometheus exporter |
| 🟢 P2 | 10.2 集团日志中心 |
| 🟢 P2 | 6.2 CMDB（如客户有） |
| 🟢 P2 | 8.1 流量回放设备（如客户有） |

---

## 12. 接口规范约定（统一标准）

### 12.1 协议约定

| 维度 | 默认约定 |
| --- | --- |
| 传输协议 | HTTPS over IPv6（招标 §1 强制）+ IPv4 双栈过渡 |
| 数据格式 | JSON over HTTP；syslog 走 RFC 5424 |
| 认证 | OAuth 2.0 / mTLS（机器对机器）；API Key 仅限内部 |
| 字符编码 | UTF-8 |
| 时间戳 | ISO 8601 + 时区（如 `2026-05-13T08:00:00+08:00`） |

### 12.2 错误处理

| 维度 | 约定 |
| --- | --- |
| 重试策略 | 指数退避（1s / 2s / 4s / 8s，最多 5 次） |
| 超时 | 出向接口默认 30 秒；查询类 10 秒 |
| 失败降级 | NxSOC 失败 → 标记验证结果为"待人工确认"；不阻塞 inject 执行 |
| 错误日志 | 全部错误落 Elasticsearch + 告警到运维 |

### 12.3 安全约束

| 维度 | 约定 |
| --- | --- |
| 凭据存储 | 平台 `encryption_key` + `encryption_salt` 加密存储敏感字段（API Key / Password） |
| 网络隔离 | 出向接口走专用 egress；不允许公网直连（合规要求） |
| 审计日志 | 所有接口调用记录调用人 / 时间 / 参数（脱敏） |
| 速率限制 | 出向接口对各厂商 API 设速率限制（避免被封） |

---

## 13. 接口与待澄清清单对照

| 接口 | 关联待澄清项 | 优先级 |
| --- | --- | --- |
| 1.1 NxSOC | 待澄清 §1.1 | 🔴 阻塞 |
| 4.1 CAPE | 待澄清 §1.2 | 🔴 阻塞 |
| 6.1 集团 IPv6 平台 | 待澄清 §1.7 | 🔴 阻塞 |
| 2.1 OpenAEV Agent | 待澄清 §1.3 靶机部署 | 🔴 阻塞 |
| 5.1 协作主机 Agent | 待澄清 §1.5 部署位置 | 🔴 阻塞 |
| 2.7 国产 EDR | 待澄清 §1.4 客户 EDR 现状 | 🔴 阻塞 |
| 3.1 WAF/IPS 策略 | 待澄清 §2.1.1 安全设备清单 | 🟡 重要 |
| 7.1 SMTP | 待澄清 §2.1.2 SMTP 配置 | 🟡 重要 |
| 9.1 / 9.2 认证 | 待澄清 §3.1 用户权限 | 🟢 一般 |
| 10.1 / 10.2 监控 | 待澄清 §3.2 SLA + §3.5 报告 | 🟢 一般 |

---

## 14. 一句话总结

Veriguard 平台需对接 **10 大类共 23 个外部接口** 才能完成"模拟攻击 → 检测设备响应 → 告警回流 → 结果判定"的验证闭环。其中 **6 个阻塞性接口**（NxSOC / CAPE / 集团 IPv6 平台 / OpenAEV Agent / 协作主机 Agent / 国产 EDR）必须在项目启动第 1-3 周内拿到接口文档或客户答复，否则核心研发模块（nxsoc_adapter / cape_adapter / veriguard-agent 子项目 / 协作主机 Agent 子项目）无法启动。**6 个 EDR / SMTP / SSO 接口** 上游 OpenAEV 已实现完整适配代码，甲方提供配置参数即可开通。**3 个监控 / 日志 / CMDB 接口** 为运维侧 nice-to-have，试运行期补齐即可。

---

*文档版本 v1.0，截至 2026-05-13；本清单与 [`IPv6安全验证系统-甲方待澄清清单.md`](./IPv6安全验证系统-甲方待澄清清单.md) §1-§3 紧密联动，每次客户对齐会后同步更新。*
