# OpenAEV 相对 Veriguard 的 PRD 符合性分析

## 说明

- 基准产品：**Veriguard（自研 BAS 系统）**
- 对比对象：**OpenAEV / 原 OpenBAS**
- 对照基线：[产品要求.md](/Users/lamba/github/Veriguard/docs/prd/产品要求.md)
- 分析方式：基于 OpenAEV 源码静态分析
- 对应源码版本：`dd437fb`
- 结论口径：判断 **OpenAEV 是否能够完全满足 Veriguard 当前 PRD**，以及哪些能力仅可作为实现参考

## 总体结论

**不能完全满足。**

如果以 Veriguard 的 PRD 为验收标准，OpenAEV 当前更适合作为：

- 场景编排与演练平台的参考实现
- 终端/主机侧 BAS 能力的部分参考底座
- 集成框架、调度框架、执行链路的参考对象

但它**不能作为完整满足该 PRD 的直接替代品**。最大的缺口集中在：

- 流量安全验证
- 沙箱管理
- 部分攻击编排能力
- 明确的用例规模和攻击类型覆盖证明

## 一级结论总览

| PRD 模块 | 结论 | 判断 |
| --- | --- | --- |
| 2.1 流量安全验证 | 不满足 | 无专门流量侧验证框架、无 pcap / NTA / IDS / 四元组能力闭环 |
| 2.2 应用与服务器安全验证 | 部分满足 | 有 atomic testing、payload、agent、executor、期望校验，但无法证明满足 HIDS 场景覆盖和用例规模 |
| 2.3 自定义验证 | 部分满足 | 支持自定义场景、导入、附件、命令、邮件类注入，但不支持 pcap、web 攻击包、动态筛选场景 |
| 2.4 攻击编排 | 部分满足 | 支持路径图、时间线、依赖条件，但缺少重复执行、全局执行模式、SOC 关联规则验证等 |
| 2.5 沙箱管理 | 不满足 | 无沙箱管理子系统、网络控制、回滚恢复、样本执行沙箱 |

## 一、2.1 流量安全验证

### 结论

**不满足。**

### 源码依据

OpenAEV 的核心能力集中在：

- `atomic_testing`
- `inject`
- `payload`
- `executor`
- `agent`

关键入口和模块见：

- [AtomicTestingApi.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/rest/atomic_testing/AtomicTestingApi.java)
- [Executor.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/executors/Executor.java)
- [ExecutionExecutorService.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/execution/ExecutionExecutorService.java)

从源码中未发现以下能力闭环：

- 网络边界资产覆盖度分析
- 流量安全设备覆盖度判定
- NTA / IDS / WAF 流量验证框架
- `pcap` 上传、流量回放、四元组编排
- 针对流量设备稳定性趋势的专门统计逻辑

### 对 Veriguard 的意义

OpenAEV 在这部分**不能作为 Veriguard 流量侧验证子系统的直接参考实现**。  
如果 Veriguard 要满足该 PRD，流量验证能力仍需自建或单独引入网络侧执行/回放/检测框架。

## 二、2.2 应用与服务器安全验证

### 结论

**部分满足。**

### 已具备的能力

OpenAEV 具备较完整的主机/终端侧 BAS 基础设施：

- 原子测试创建、修改、执行、重放
- payload 管理、导入导出、命令与前置条件
- agent / executor / external injector 多路径执行
- expectation / trace / finding 结果采集

相关源码：

- [AtomicTestingApi.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/rest/atomic_testing/AtomicTestingApi.java)
- [PayloadApi.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/rest/payload/PayloadApi.java)
- [InjectsExecutionJob.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/scheduler/jobs/InjectsExecutionJob.java)
- [ExecutionExecutorService.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/execution/ExecutionExecutorService.java)

它还支持多种终端执行器集成：

- Caldera
- CrowdStrike
- SentinelOne
- Tanium
- Palo Alto Cortex

### 缺失或无法证明的部分

OpenAEV 源码**无法直接证明**以下 PRD 条件成立：

- 支持 HIDS 场景的系统化效用验证
- 明确覆盖“反弹 shell、webshell 落盘、提权、持久化、病毒样本落盘”等不少于 10 类攻击
- 总用例数量不低于 300 个

OpenAEV 有 starter pack 和 payload / injector contract 体系，但其规模和分类无法等价证明满足该条 PRD。

### 对 Veriguard 的意义

OpenAEV 在主机侧 BAS 的**执行框架**和**结果框架**上有借鉴价值，但在 Veriguard 的 PRD 口径下，仍需要：

- 补齐主机侧能力矩阵
- 补齐用例数量和攻击分类证明
- 将“执行器集成”提升为“主机安全验证产品能力”

## 三、2.3 自定义验证

### 结论

**部分满足。**

### 已具备的能力

OpenAEV 支持：

- 自定义 Scenario / Inject / Payload
- 批量导入 injects
- 批量导入 atomic testings
- 上传文件附件
- 执行命令
- 邮件/Channel/Manual/Challenge 等多类 injector

相关源码：

- [ScenarioAssistantDrawer.tsx](/tmp/openaev/openaev-front/src/admin/components/scenarios/scenario/scenario_assistant/ScenarioAssistantDrawer.tsx)
- [InjectImportService.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/service/InjectImportService.java)
- [PayloadApi.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/rest/payload/PayloadApi.java)
- [InjectorContractService.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/rest/injector_contract/InjectorContractService.java)
- [ContractAttachment.java](/tmp/openaev/openaev-framework/src/main/java/io/openaev/injector_contract/fields/ContractAttachment.java)

并且它具备 ATT&CK 视角的场景助手能力：

- 选择 TTP
- 按资产/资产组生成 inject
- 结合 `domains` 做一定的安全域分类

### 不满足的部分

OpenAEV 没有看到以下明确能力：

- `pcap` 上传验证
- 专门的 web 攻击包构造器
- 从全量用例筛选后形成**动态场景**，并在用例更新时自动进入场景

它的导入更像“批量导入对象”，不是“按筛选条件自动维护场景成员”。

### 对 Veriguard 的意义

OpenAEV 的合同模型（injector contract / payload / attachment / expectation）很适合 Veriguard 参考；  
但 Veriguard 若要对齐 PRD，仍需单独建设：

- 流量型自定义用例
- 动态筛选场景
- 面向网络/主机不同面的统一用例工厂

## 四、2.4 攻击编排

### 结论

**部分满足。**

### 已具备的能力

OpenAEV 在编排层面已经相当成熟，源码能证明它支持：

- 可视化路径图
- inject 节点依赖关系
- 时间偏移 `inject_depends_duration`
- 依赖条件 `dependency_condition`
- AND / OR 逻辑
- 时间线展示和路径编辑

关键源码：

- [ChainedTimeline.tsx](/tmp/openaev/openaev-front/src/components/ChainedTimeline.tsx)
- [InjectChainsForm.tsx](/tmp/openaev/openaev-front/src/admin/components/common/injects/InjectChainsForm.tsx)
- [InjectsExecutionJob.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/scheduler/jobs/InjectsExecutionJob.java)

### 不满足的部分

与 Veriguard PRD 相比，OpenAEV 仍缺：

- 每节点重复执行次数与间隔
- 编排任务级“全局统一参数 + 节点独立参数”
- 显式执行模式：
  - 无论是否拦截都继续
  - 被拦截后停止
- SOC 关联告警规则验证
- SOC 规则匹配条件编排
- 全链路有效 / 全链路失效 / 部分失效 的显式链路级结果模型

OpenAEV 的依赖条件本质上更像“前置条件链”，还不是你 PRD 所描述的“验证编排控制台”完整形态。

### 对 Veriguard 的意义

这部分是 OpenAEV **最值得参考**的区域，尤其是：

- 路径图编辑交互
- 依赖条件建模
- 时间线与链路视图

但 Veriguard 需要在此基础上继续补足：

- 编排任务运行模式
- 节点级重复/延迟策略
- SOC 规则验证闭环
- 链路级结果归因

## 五、2.5 沙箱管理

### 结论

**不满足。**

### 源码依据

源码中没有看到独立的：

- 沙箱管理模块
- 恶意样本执行沙箱
- 沙箱网络访问控制
- 执行后快照回滚 / 环境还原

虽然开发环境中有 [caldera.yml](/tmp/openaev/openaev-dev/caldera.yml) 和 Caldera 执行器集成：

- [CalderaExecutorIntegration.java](/tmp/openaev/openaev-api/src/main/java/io/openaev/integration/impl/executors/caldera/CalderaExecutorIntegration.java)

但这属于外部执行平台集成，**不等于沙箱管理子系统**。

### 对 Veriguard 的意义

如果 Veriguard 的 PRD 需要“真实恶意样本 + 沙箱隔离 + 自动回滚”，这部分不能参考 OpenAEV 现有实现，必须自建独立沙箱体系。

## 六、对 Veriguard 的总体判断

从 Veriguard 的 PRD 目标看，OpenAEV 更像：

- **主机/终端侧 BAS 控制平面参考实现**
- **场景编排与执行框架参考实现**
- **集成管理框架参考实现**

但它不是：

- 完整流量验证平台
- 完整沙箱验证平台
- 已经满足 Veriguard PRD 的现成替代产品

## 七、最终结论

如果问题是：

> OpenAEV 是否能够从源码层面完全满足 `docs/prd/产品要求.md` 的功能要求？

答案是：

**不能。**

如果问题改成：

> OpenAEV 是否能为 Veriguard 提供部分技术路径参考？

答案是：

**可以，重点集中在 2.2、2.3、2.4 的执行框架与编排框架；2.1 和 2.5 参考价值较低。**

## 八、建议的后续动作

建议下一步把 Veriguard 的目标能力拆成三层：

1. **可直接借鉴 OpenAEV 的能力**
   - 场景/演练/注入建模
   - 路径图编排
   - 异步执行与结果回流
   - 集成工厂机制

2. **需要基于 OpenAEV 思路增强的能力**
   - 主机验证能力矩阵
   - 编排模式与链路级结果判定
   - 动态筛选场景

3. **需要 Veriguard 自建的新能力**
   - 流量安全验证
   - 沙箱管理
   - SOC 关联规则验证
