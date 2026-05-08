# Veriguard 沙箱二开 M1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 Veriguard 沙箱二开的 M1：控制台 UX 完善 + 边界声明 + 抽象 `SandboxDriver` 接口骨架。M1 收尾后 PRD §2.5 中"沙箱平台 CRUD"与"网络访问控制策略配置"两条可拍截图验收；自动还原仅有表单校验，分析机可视化与样本提交留待 M2 / M3。

**Architecture:** 在现有 `veriguard-api` Spring Boot 单体内增量扩展。新增包 `io.veriguard.integration.sandbox`（含 capev2 子包）承载驱动适配；将沙箱业务从 `SecurityValidationService/Api` 拆出到 `SandboxService/SandboxApi/SandboxScriptExporter`。前端在 `admin/components/veriguard/sandbox/` 落新组件，沿用现有 React 19 + MUI + 受控组件模式，不引入新依赖。M1 不调用 CAPEv2，驱动层只占位。

**Tech Stack:** Java 21、Spring Boot 3.3.7、JPA + Flyway（Java migration）、JUnit 5 + Mockito + AssertJ、MockMvc；React 19 + TypeScript + MUI v7、vitest、Playwright。

**参考 spec:** `docs/superpowers/specs/2026-04-26-veriguard-sandbox-design.md`（§4.2 / §5 / §6 / §8 / §10.1 / §10.5）

---

## 文件影响清单

### 新建
- `veriguard-api/src/main/java/io/veriguard/migration/V4_73__Extend_veriguard_sandbox.java`
- `veriguard-api/src/main/java/io/veriguard/integration/sandbox/SandboxDriver.java`
- `veriguard-api/src/main/java/io/veriguard/integration/sandbox/SandboxDriverRegistry.java`
- `veriguard-api/src/main/java/io/veriguard/integration/sandbox/NotImplementedSandboxDriver.java`
- `veriguard-api/src/main/java/io/veriguard/integration/sandbox/SandboxIntegrationException.java`
- `veriguard-api/src/main/java/io/veriguard/integration/sandbox/dto/MachineSnapshot.java`
- `veriguard-api/src/main/java/io/veriguard/integration/sandbox/dto/SubmissionResult.java`
- `veriguard-api/src/main/java/io/veriguard/integration/sandbox/dto/SampleSubmissionRequest.java`
- `veriguard-api/src/main/java/io/veriguard/integration/sandbox/dto/SandboxTaskStatus.java`
- `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxApi.java`
- `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxService.java`
- `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxScriptExporter.java`
- `veriguard-api/src/test/java/io/veriguard/integration/sandbox/NotImplementedSandboxDriverTest.java`
- `veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxServiceTest.java`
- `veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxScriptExporterTest.java`
- `veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxApiIntegrationTest.java`
- `veriguard-front/src/admin/components/veriguard/sandbox/SandboxList.tsx`
- `veriguard-front/src/admin/components/veriguard/sandbox/SandboxDialog.tsx`
- `veriguard-front/src/admin/components/veriguard/sandbox/NetworkRuleEditor.tsx`
- `veriguard-front/src/admin/components/veriguard/sandbox/NetworkRuleExportButtons.tsx`
- `veriguard-front/src/admin/components/veriguard/sandbox/DeleteConfirmDialog.tsx`
- `veriguard-front/src/admin/components/veriguard/sandbox/utils/cidr-port-validators.ts`
- `veriguard-front/src/admin/components/veriguard/sandbox/__tests__/NetworkRuleEditor.test.tsx`
- `veriguard-front/src/admin/components/veriguard/sandbox/__tests__/SandboxDialog.test.tsx`
- `veriguard-front/src/admin/components/veriguard/sandbox/utils/__tests__/cidr-port-validators.test.ts`
- `veriguard-front/tests_e2e/admin/veriguard/sandbox/m1.spec.ts`

### 修改
- `veriguard-model/src/main/java/io/veriguard/database/model/VeriguardSandbox.java`（删除 `endpoint`、`providerType` 字段及其 `ProviderType` 枚举）
- `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxInput.java`（删字段、去 `@AssertTrue` / `@NotEmpty`）
- `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxMapper.java`（同步收窄）
- `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SecurityValidationDtos.java`（`SandboxOutput` 删字段）
- `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SecurityValidationApi.java`（移除沙箱端点）
- `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SecurityValidationService.java`（移除沙箱方法）
- `veriguard-api/src/main/java/io/veriguard/rest/helper/RestBehavior.java`（新增 `SandboxIntegrationException` handler）
- `veriguard-front/src/admin/components/veriguard/VeriguardConsole.tsx`（沙箱 Tab 渲染拆出后的组件）
- `veriguard-front/src/actions/veriguard/veriguard-actions.ts`（DTO 收窄、新增 export actions）
- `docs/参考资料/Veriguard二开落地说明.md`（同步 M1 落地说明）

### 错误码契约约定（适配项目现有 `ValidationErrorBag`）

项目 `RestBehavior` 已经使用 `ValidationErrorBag { errors: { children: { <key>: { errors: [{message}] } } } }` 作为错误响应。本计划沿用同一结构：

- `InputValidationException(field, message)`：`field` 取 spec §8.2 的 `reason_code`（snake_case）。
- `SandboxIntegrationException(reasonCode, message)`：handler 用 `reasonCode.name().toLowerCase()` 作为 children key，`bag.code = "SANDBOX_INTEGRATION_FAILED"`，HTTP 状态 502 / 504。
- `DataIntegrityViolationException`（唯一约束）：在 `SandboxService` 内主动转 `InputValidationException("sandbox_name_duplicated", ...)`，避免侵入 `RestBehavior`。

> Spec §8 的"独立 `error_code/reason_code` envelope"已在本计划落地时适配为项目现有 envelope；spec §8.1 / §8.2 / §8.3 已同步更新（见 spec 文件）。

---

## Task 0: 创建工作 worktree

**Files:**
- 无（仅 git）

- [ ] **Step 1: 从 main 创建 worktree 与分支**

```bash
cd /Users/lamba/github/Veriguard
git worktree add worktrees/sandbox-m1 -b feature/sandbox-m1 main
cd worktrees/sandbox-m1
```

预期：worktree 目录存在；`git status` 在 `feature/sandbox-m1` 分支干净。

**后续所有 Task 的工作目录均为 `worktrees/sandbox-m1/`。** 命令路径以 worktree 根为相对根。

---

## Task 1: 驱动 DTO + 接口 + 异常 + 占位驱动

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/SandboxDriver.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/SandboxIntegrationException.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/SandboxDriverRegistry.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/NotImplementedSandboxDriver.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/dto/MachineSnapshot.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/dto/SubmissionResult.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/dto/SampleSubmissionRequest.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/dto/SandboxTaskStatus.java`
- Test: `veriguard-api/src/test/java/io/veriguard/integration/sandbox/NotImplementedSandboxDriverTest.java`

- [ ] **Step 1: 写失败测试 `NotImplementedSandboxDriverTest`**

`veriguard-api/src/test/java/io/veriguard/integration/sandbox/NotImplementedSandboxDriverTest.java`:
```java
package io.veriguard.integration.sandbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import org.junit.jupiter.api.Test;

class NotImplementedSandboxDriverTest {

  private final NotImplementedSandboxDriver driver = new NotImplementedSandboxDriver();

  @Test
  void healthCheck_throwsIntegrationException() {
    assertThatThrownBy(driver::healthCheck)
        .isInstanceOf(SandboxIntegrationException.class)
        .extracting("reasonCode")
        .isEqualTo(SandboxIntegrationException.ReasonCode.NOT_IMPLEMENTED);
  }

  @Test
  void listMachines_throwsIntegrationException() {
    assertThatThrownBy(driver::listMachines)
        .isInstanceOf(SandboxIntegrationException.class);
  }

  @Test
  void submitSample_throwsIntegrationException() {
    SampleSubmissionRequest request =
        new SampleSubmissionRequest("preset-1", "RANSOMWARE", "demo.exe",
            "deadbeef", new byte[] {0x4d, 0x5a}, null, null);
    assertThatThrownBy(() -> driver.submitSample(request))
        .isInstanceOf(SandboxIntegrationException.class);
  }

  @Test
  void fetchTaskStatus_throwsIntegrationException() {
    assertThatThrownBy(() -> driver.fetchTaskStatus(1L))
        .isInstanceOf(SandboxIntegrationException.class);
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl veriguard-api -am test -Dtest=NotImplementedSandboxDriverTest
```

预期：编译失败（类不存在）。

- [ ] **Step 3: 创建 DTO records**

`veriguard-api/src/main/java/io/veriguard/integration/sandbox/dto/MachineSnapshot.java`:
```java
package io.veriguard.integration.sandbox.dto;

import java.time.Instant;

public record MachineSnapshot(
    String name, String label, String platform, String snapshot, String status, Instant fetchedAt) {}
```

`SubmissionResult.java`:
```java
package io.veriguard.integration.sandbox.dto;

public record SubmissionResult(long capeTaskId) {}
```

`SampleSubmissionRequest.java`:
```java
package io.veriguard.integration.sandbox.dto;

public record SampleSubmissionRequest(
    String sandboxPresetId,
    String sampleType,
    String originalFilename,
    String sampleSha256,
    byte[] content,
    String targetMachineName,
    Integer timeoutSeconds) {}
```

`SandboxTaskStatus.java`:
```java
package io.veriguard.integration.sandbox.dto;

public record SandboxTaskStatus(Status status, String rawRemoteStatus, String errorMessage) {

  public enum Status {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    UNKNOWN
  }
}
```

- [ ] **Step 4: 创建 `SandboxIntegrationException`**

`veriguard-api/src/main/java/io/veriguard/integration/sandbox/SandboxIntegrationException.java`:
```java
package io.veriguard.integration.sandbox;

public class SandboxIntegrationException extends RuntimeException {

  public enum ReasonCode {
    NOT_IMPLEMENTED,
    CONNECTION_FAILED,
    AUTHENTICATION_FAILED,
    REMOTE_ERROR,
    PROTOCOL_MISMATCH,
    TIMEOUT
  }

  private final ReasonCode reasonCode;
  private final Integer remoteStatusCode;

  public SandboxIntegrationException(ReasonCode reasonCode, String message) {
    this(reasonCode, message, null, null);
  }

  public SandboxIntegrationException(ReasonCode reasonCode, String message, Throwable cause) {
    this(reasonCode, message, null, cause);
  }

  public SandboxIntegrationException(
      ReasonCode reasonCode, String message, Integer remoteStatusCode, Throwable cause) {
    super(message, cause);
    this.reasonCode = reasonCode;
    this.remoteStatusCode = remoteStatusCode;
  }

  public ReasonCode getReasonCode() {
    return reasonCode;
  }

  public Integer getRemoteStatusCode() {
    return remoteStatusCode;
  }
}
```

- [ ] **Step 5: 创建 `SandboxDriver` 接口**

`veriguard-api/src/main/java/io/veriguard/integration/sandbox/SandboxDriver.java`:
```java
package io.veriguard.integration.sandbox;

import io.veriguard.integration.sandbox.dto.MachineSnapshot;
import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import io.veriguard.integration.sandbox.dto.SandboxTaskStatus;
import io.veriguard.integration.sandbox.dto.SubmissionResult;
import java.util.List;

public interface SandboxDriver {

  void healthCheck();

  List<MachineSnapshot> listMachines();

  SubmissionResult submitSample(SampleSubmissionRequest request);

  SandboxTaskStatus fetchTaskStatus(long capeTaskId);
}
```

- [ ] **Step 6: 创建 `NotImplementedSandboxDriver`**

`veriguard-api/src/main/java/io/veriguard/integration/sandbox/NotImplementedSandboxDriver.java`:
```java
package io.veriguard.integration.sandbox;

import io.veriguard.integration.sandbox.dto.MachineSnapshot;
import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import io.veriguard.integration.sandbox.dto.SandboxTaskStatus;
import io.veriguard.integration.sandbox.dto.SubmissionResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotImplementedSandboxDriver implements SandboxDriver {

  @Override
  public void healthCheck() {
    throw notImplemented("healthCheck");
  }

  @Override
  public List<MachineSnapshot> listMachines() {
    throw notImplemented("listMachines");
  }

  @Override
  public SubmissionResult submitSample(SampleSubmissionRequest request) {
    throw notImplemented("submitSample");
  }

  @Override
  public SandboxTaskStatus fetchTaskStatus(long capeTaskId) {
    throw notImplemented("fetchTaskStatus");
  }

  private SandboxIntegrationException notImplemented(String operation) {
    return new SandboxIntegrationException(
        SandboxIntegrationException.ReasonCode.NOT_IMPLEMENTED,
        "Sandbox driver operation '" + operation + "' is not implemented in M1.");
  }
}
```

- [ ] **Step 7: 创建 `SandboxDriverRegistry`（M1 单驱动工厂）**

`veriguard-api/src/main/java/io/veriguard/integration/sandbox/SandboxDriverRegistry.java`:
```java
package io.veriguard.integration.sandbox;

import org.springframework.stereotype.Component;

@Component
public class SandboxDriverRegistry {

  private final SandboxDriver driver;

  public SandboxDriverRegistry(SandboxDriver driver) {
    this.driver = driver;
  }

  public SandboxDriver driver() {
    return driver;
  }
}
```

- [ ] **Step 8: 跑测试确认通过**

```bash
mvn -pl veriguard-api -am test -Dtest=NotImplementedSandboxDriverTest
```

预期：4 个测试全部 PASS。

- [ ] **Step 9: 提交**

```bash
git add veriguard-api/src/main/java/io/veriguard/integration/sandbox \
        veriguard-api/src/test/java/io/veriguard/integration/sandbox
git commit -m "feat(sandbox): scaffold sandbox driver interface and not-implemented driver

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 2: `SandboxIntegrationException` HTTP 映射

**Files:**
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/helper/RestBehavior.java`
- Test: `veriguard-api/src/test/java/io/veriguard/integration/sandbox/SandboxIntegrationExceptionMappingTest.java`

- [ ] **Step 1: 写失败测试 `SandboxIntegrationExceptionMappingTest`**

新建 `veriguard-api/src/test/java/io/veriguard/integration/sandbox/SandboxIntegrationExceptionMappingTest.java`:
```java
package io.veriguard.integration.sandbox;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.rest.helper.RestBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SandboxIntegrationExceptionMappingTest.ProbeController.class)
@Import({RestBehavior.class})
class SandboxIntegrationExceptionMappingTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void timeout_maps_to_504_with_reason_code_key() throws Exception {
    mockMvc
        .perform(get("/__probe/timeout"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.errors.children.timeout").exists());
  }

  @Test
  void authentication_failed_maps_to_502() throws Exception {
    mockMvc
        .perform(get("/__probe/auth"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.errors.children.authentication_failed").exists());
  }

  @RestController
  static class ProbeController {

    @GetMapping("/__probe/timeout")
    public void timeout() {
      throw new SandboxIntegrationException(
          SandboxIntegrationException.ReasonCode.TIMEOUT, "remote took too long");
    }

    @GetMapping("/__probe/auth")
    public void auth() {
      throw new SandboxIntegrationException(
          SandboxIntegrationException.ReasonCode.AUTHENTICATION_FAILED, "bad token");
    }
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl veriguard-api -am test -Dtest=SandboxIntegrationExceptionMappingTest
```

预期：FAIL（handler 尚未注册，将被默认 500 兜底）。

- [ ] **Step 3: 在 `RestBehavior` 末尾添加 handler**

`veriguard-api/src/main/java/io/veriguard/rest/helper/RestBehavior.java` 在 `AlreadyExistingException` handler 之后追加（保留现有 import；新增 import：`io.veriguard.integration.sandbox.SandboxIntegrationException`）：
```java
  @ExceptionHandler(SandboxIntegrationException.class)
  public ResponseEntity<ValidationErrorBag> handleSandboxIntegrationException(
      SandboxIntegrationException ex) {
    HttpStatus status =
        ex.getReasonCode() == SandboxIntegrationException.ReasonCode.TIMEOUT
            ? HttpStatus.GATEWAY_TIMEOUT
            : HttpStatus.BAD_GATEWAY;
    log.warn(
        "Sandbox integration failed: reason={} remoteStatus={} message={}",
        ex.getReasonCode(),
        ex.getRemoteStatusCode(),
        ex.getMessage());
    ValidationErrorBag bag =
        new ValidationErrorBag(status.value(), "SANDBOX_INTEGRATION_FAILED");
    ValidationError errors = new ValidationError();
    Map<String, ValidationContent> errorsBag = new HashMap<>();
    errorsBag.put(
        ex.getReasonCode().name().toLowerCase(), new ValidationContent(ex.getMessage()));
    errors.setChildren(errorsBag);
    bag.setErrors(errors);
    return ResponseEntity.status(status).body(bag);
  }
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl veriguard-api -am test -Dtest=SandboxIntegrationExceptionMappingTest
```

预期：2 个测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add veriguard-api/src/main/java/io/veriguard/rest/helper/RestBehavior.java \
        veriguard-api/src/test/java/io/veriguard/integration/sandbox/SandboxIntegrationExceptionMappingTest.java
git commit -m "feat(sandbox): map SandboxIntegrationException to 502/504 with reason_code

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 3: 收窄 `VeriguardSandbox` 实体（删 endpoint / providerType / 枚举）

**Files:**
- Modify: `veriguard-model/src/main/java/io/veriguard/database/model/VeriguardSandbox.java`

- [ ] **Step 1: 删除 `ProviderType` 枚举与对应字段**

打开 `veriguard-model/src/main/java/io/veriguard/database/model/VeriguardSandbox.java`，删除如下片段：

```java
  public enum ProviderType {
    VMWARE,
    OPENSTACK,
    KVM,
    KUBERNETES,
    CUSTOM
  }
```

删除字段：
```java
  @Column(name = "veriguard_sandbox_provider_type")
  @Enumerated(EnumType.STRING)
  @JsonProperty("sandbox_provider_type")
  @NotNull
  private ProviderType providerType;

  @Column(name = "veriguard_sandbox_endpoint")
  @JsonProperty("sandbox_endpoint")
  @NotBlank
  private String endpoint;
```

剩余字段保持原样（`name / description / networkPolicy / networkRules / autoRestoreEnabled / supportedSampleTypes / status / createdAt / updatedAt`）。

- [ ] **Step 2: 编译确认尚有依赖断裂**

```bash
mvn -pl veriguard-model -am compile
mvn -pl veriguard-api -am compile
```

预期：`veriguard-api` 在 `SandboxInput` / `SandboxMapper` / `SandboxOutput` / `SecurityValidationService` 等处编译失败（这是预期，下面 Task 4–7 会修）。

> ⚠️ Task 3–7 形成一个不可分割的编译单元；建议在本 worktree 上把它们一气呵成，最后一次性 `mvn compile` 通过再单独 commit。或者在 Task 7 末尾合并提交。本计划选择"末尾合并提交"——见 Task 7 Step 6。

---

## Task 4: 收窄 `SandboxInput`（删字段、去 `@AssertTrue` / `@NotEmpty`）

**Files:**
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxInput.java`

- [ ] **Step 1: 重写 `SandboxInput`**

`veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxInput.java` 完整替换为：
```java
package io.veriguard.rest.security_validation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.VeriguardSandbox;
import io.veriguard.database.model.VeriguardSandbox.SampleType;
import io.veriguard.database.model.VeriguardSandboxNetworkRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SandboxInput(
    @JsonProperty("sandbox_name") @NotBlank String name,
    @JsonProperty("sandbox_description") String description,
    @JsonProperty("sandbox_network_policy") @NotNull VeriguardSandbox.NetworkPolicy networkPolicy,
    @JsonProperty("sandbox_network_rules") @NotNull
        List<@Valid VeriguardSandboxNetworkRule> networkRules,
    @JsonProperty("sandbox_auto_restore_enabled") boolean autoRestoreEnabled,
    @JsonProperty("sandbox_supported_sample_types") @NotNull List<@NotNull SampleType> supportedSampleTypes,
    @JsonProperty("sandbox_status") @NotNull VeriguardSandbox.Status status) {}
```

变化：
- 删除 `sandbox_provider_type`、`sandbox_endpoint`。
- `networkRules` 由 `@NotEmpty` 改为 `@NotNull`（允许空列表）。
- `supportedSampleTypes` 由 `@NotEmpty` 改为 `@NotNull`（业务规则在 service 层）。
- `autoRestoreEnabled` 移除 `@AssertTrue`（业务规则在 service 层）。

---

## Task 5: 收窄 `SandboxOutput` 与 `SandboxMapper`

**Files:**
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SecurityValidationDtos.java`
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxMapper.java`

- [ ] **Step 1: 修改 `SecurityValidationDtos.SandboxOutput`**

打开 `SecurityValidationDtos.java`，定位 `record SandboxOutput(...)`（spec 中现有）。原 record 包含 `providerType` / `endpoint`；删除这两项。改后：
```java
  public record SandboxOutput(
      @JsonProperty("sandbox_id") String id,
      @JsonProperty("sandbox_name") String name,
      @JsonProperty("sandbox_description") String description,
      @JsonProperty("sandbox_network_policy") VeriguardSandbox.NetworkPolicy networkPolicy,
      @JsonProperty("sandbox_network_rules") List<VeriguardSandboxNetworkRule> networkRules,
      @JsonProperty("sandbox_auto_restore_enabled") boolean autoRestoreEnabled,
      @JsonProperty("sandbox_supported_sample_types") List<VeriguardSandbox.SampleType> supportedSampleTypes,
      @JsonProperty("sandbox_status") VeriguardSandbox.Status status,
      @JsonProperty("sandbox_created_at") Instant createdAt,
      @JsonProperty("sandbox_updated_at") Instant updatedAt) {}
```

> 实际原 record 字段名顺序 / 注解请以 `SecurityValidationDtos.java` 现状为准；保留所有不在删除清单内的字段不动。

- [ ] **Step 2: 修改 `SandboxMapper`**

`veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxMapper.java` 完整替换为：
```java
package io.veriguard.rest.security_validation;

import io.veriguard.database.model.VeriguardSandbox;
import java.util.ArrayList;

public final class SandboxMapper {

  private SandboxMapper() {}

  public static SecurityValidationDtos.SandboxOutput toOutput(VeriguardSandbox sandbox) {
    return new SecurityValidationDtos.SandboxOutput(
        sandbox.getId(),
        sandbox.getName(),
        sandbox.getDescription(),
        sandbox.getNetworkPolicy(),
        sandbox.getNetworkRules(),
        sandbox.isAutoRestoreEnabled(),
        sandbox.getSupportedSampleTypes(),
        sandbox.getStatus(),
        sandbox.getCreatedAt(),
        sandbox.getUpdatedAt());
  }

  public static void updateEntity(VeriguardSandbox sandbox, SandboxInput input) {
    sandbox.setName(input.name());
    sandbox.setDescription(input.description());
    sandbox.setNetworkPolicy(input.networkPolicy());
    sandbox.setNetworkRules(new ArrayList<>(input.networkRules()));
    sandbox.setAutoRestoreEnabled(input.autoRestoreEnabled());
    sandbox.setSupportedSampleTypes(new ArrayList<>(input.supportedSampleTypes()));
    sandbox.setStatus(input.status());
  }
}
```

变化：删除 `setProviderType` / `setEndpoint` 调用与 output 中对应字段。

---

## Task 6: 拆出 `SandboxService`

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxService.java`
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SecurityValidationService.java`
- Test: `veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxServiceTest.java`

- [ ] **Step 1: 写失败测试 `SandboxServiceTest`**

新建 `veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxServiceTest.java`:
```java
package io.veriguard.rest.security_validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.VeriguardSandbox;
import io.veriguard.database.model.VeriguardSandbox.NetworkPolicy;
import io.veriguard.database.model.VeriguardSandbox.SampleType;
import io.veriguard.database.model.VeriguardSandbox.Status;
import io.veriguard.database.model.VeriguardSandboxNetworkRule;
import io.veriguard.database.repository.VeriguardSandboxRepository;
import io.veriguard.rest.exception.InputValidationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SandboxServiceTest {

  @Mock private VeriguardSandboxRepository repository;
  @InjectMocks private SandboxService service;

  private SandboxInput baseInput;

  @BeforeEach
  void setUp() {
    baseInput =
        new SandboxInput(
            "勒索沙箱",
            "ransomware preset",
            NetworkPolicy.DENY_ALL,
            List.of(),
            true,
            List.of(SampleType.RANSOMWARE),
            Status.ACTIVE);
  }

  @Test
  void create_persists_sandbox_with_empty_network_rules() throws Exception {
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SecurityValidationDtos.SandboxOutput output = service.createSandbox(baseInput);

    assertThat(output.networkRules()).isEmpty();
    assertThat(output.autoRestoreEnabled()).isTrue();
  }

  @Test
  void create_rejects_auto_restore_disabled() {
    SandboxInput input =
        new SandboxInput(
            baseInput.name(),
            baseInput.description(),
            baseInput.networkPolicy(),
            baseInput.networkRules(),
            false,
            baseInput.supportedSampleTypes(),
            baseInput.status());

    assertThatThrownBy(() -> service.createSandbox(input))
        .isInstanceOf(InputValidationException.class)
        .hasFieldOrPropertyWithValue("field", "sandbox_auto_restore_required");
  }

  @Test
  void create_rejects_empty_sample_types() {
    SandboxInput input =
        new SandboxInput(
            baseInput.name(),
            baseInput.description(),
            baseInput.networkPolicy(),
            baseInput.networkRules(),
            baseInput.autoRestoreEnabled(),
            List.of(),
            baseInput.status());

    assertThatThrownBy(() -> service.createSandbox(input))
        .isInstanceOf(InputValidationException.class)
        .hasFieldOrPropertyWithValue("field", "sandbox_supported_sample_types_empty");
  }

  @Test
  void create_translates_unique_violation_to_input_validation() {
    when(repository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service.createSandbox(baseInput))
        .isInstanceOf(InputValidationException.class)
        .hasFieldOrPropertyWithValue("field", "sandbox_name_duplicated");
  }

  @Test
  void create_accepts_multiple_network_rules() throws Exception {
    lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    VeriguardSandboxNetworkRule rule1 =
        new VeriguardSandboxNetworkRule(
            VeriguardSandboxNetworkRule.Direction.EGRESS,
            VeriguardSandboxNetworkRule.RuleAction.ALLOW,
            "TCP",
            "10.0.0.0/8",
            "443");
    SandboxInput input =
        new SandboxInput(
            baseInput.name(),
            baseInput.description(),
            baseInput.networkPolicy(),
            List.of(rule1, rule1),
            baseInput.autoRestoreEnabled(),
            baseInput.supportedSampleTypes(),
            baseInput.status());

    SecurityValidationDtos.SandboxOutput output = service.createSandbox(input);

    assertThat(output.networkRules()).hasSize(2);
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl veriguard-api -am test -Dtest=SandboxServiceTest
```

预期：编译失败（`SandboxService` 不存在）。

- [ ] **Step 3: 创建 `SandboxService`**

`veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxService.java`:
```java
package io.veriguard.rest.security_validation;

import static io.veriguard.rest.security_validation.SandboxMapper.toOutput;
import static io.veriguard.rest.security_validation.SandboxMapper.updateEntity;

import io.veriguard.database.model.VeriguardSandbox;
import io.veriguard.database.repository.VeriguardSandboxRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.InputValidationException;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class SandboxService {

  private final VeriguardSandboxRepository sandboxRepository;

  public SandboxService(VeriguardSandboxRepository sandboxRepository) {
    this.sandboxRepository = sandboxRepository;
  }

  public SecurityValidationDtos.SandboxOutput createSandbox(SandboxInput input)
      throws InputValidationException {
    validate(input);
    VeriguardSandbox sandbox = new VeriguardSandbox();
    updateEntity(sandbox, input);
    return persist(sandbox);
  }

  public SecurityValidationDtos.SandboxOutput updateSandbox(String sandboxId, SandboxInput input)
      throws InputValidationException {
    validate(input);
    VeriguardSandbox sandbox = findSandbox(sandboxId);
    updateEntity(sandbox, input);
    return persist(sandbox);
  }

  @Transactional(readOnly = true)
  public List<SecurityValidationDtos.SandboxOutput> sandboxes() {
    return sandboxRepository.findAll().stream().map(SandboxMapper::toOutput).toList();
  }

  @Transactional(readOnly = true)
  public SecurityValidationDtos.SandboxOutput sandbox(String sandboxId) {
    return toOutput(findSandbox(sandboxId));
  }

  public void deleteSandbox(String sandboxId) {
    sandboxRepository.delete(findSandbox(sandboxId));
  }

  private SecurityValidationDtos.SandboxOutput persist(VeriguardSandbox sandbox)
      throws InputValidationException {
    try {
      return toOutput(sandboxRepository.save(sandbox));
    } catch (DataIntegrityViolationException ex) {
      throw new InputValidationException(
          "sandbox_name_duplicated", "Sandbox with the same name already exists.");
    }
  }

  private VeriguardSandbox findSandbox(String sandboxId) {
    return sandboxRepository.findById(sandboxId).orElseThrow(ElementNotFoundException::new);
  }

  private void validate(SandboxInput input) throws InputValidationException {
    Objects.requireNonNull(input, "Sandbox input must not be null");
    if (!input.autoRestoreEnabled()) {
      throw new InputValidationException(
          "sandbox_auto_restore_required", "Sandbox auto restore must be enabled.");
    }
    if (input.supportedSampleTypes().isEmpty()) {
      throw new InputValidationException(
          "sandbox_supported_sample_types_empty",
          "At least one supported sample type is required.");
    }
  }
}
```

- [ ] **Step 4: 从 `SecurityValidationService` 移除沙箱方法**

打开 `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SecurityValidationService.java`，删除：
- 字段 `private final VeriguardSandboxRepository sandboxRepository;`
- 方法 `createSandbox / sandboxes / sandbox / updateSandbox / deleteSandbox`
- 私有方法 `findSandbox / validateSandboxInput`

保留只读方法 `capabilityMatrix / attackCatalog / orchestrationSchema / totalUseCaseTemplates / attackTypes / generatedTemplates / appendTemplates`。

构造器：因为 `@RequiredArgsConstructor` 是 Lombok 注解 + 该 service 不再依赖 sandboxRepository，无需手工改构造器，删除字段后 Lombok 自动重生成。

- [ ] **Step 5: 跑测试确认通过**

```bash
mvn -pl veriguard-api -am test -Dtest=SandboxServiceTest
```

预期：5 个测试 PASS。

---

## Task 7: 拆出 `SandboxApi`

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxApi.java`
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SecurityValidationApi.java`
- Test: `veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxApiIntegrationTest.java`

- [ ] **Step 1: 写失败集成测试 `SandboxApiIntegrationTest`**

新建 `veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxApiIntegrationTest.java`，按项目现有 `IntegrationTest` 基类模式（如已存在），最小骨架：
```java
package io.veriguard.rest.security_validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@DirtiesContext
@Transactional
@WithMockUser(authorities = {"ROLE_ADMIN"})
class SandboxApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private static final String VALID_BODY =
      """
      {
        "sandbox_name": "勒索沙箱",
        "sandbox_description": "ransomware preset",
        "sandbox_network_policy": "DENY_ALL",
        "sandbox_network_rules": [],
        "sandbox_auto_restore_enabled": true,
        "sandbox_supported_sample_types": ["RANSOMWARE"],
        "sandbox_status": "ACTIVE"
      }
      """;

  @Test
  void create_returns_201_and_persists() throws Exception {
    mockMvc
        .perform(post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sandbox_id").exists())
        .andExpect(jsonPath("$.sandbox_provider_type").doesNotExist())
        .andExpect(jsonPath("$.sandbox_endpoint").doesNotExist());
  }

  @Test
  void create_with_disabled_auto_restore_returns_400_with_reason_code() throws Exception {
    String body = VALID_BODY.replace("\"sandbox_auto_restore_enabled\": true",
        "\"sandbox_auto_restore_enabled\": false");
    mockMvc
        .perform(post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.children.sandbox_auto_restore_required").exists());
  }

  @Test
  void create_with_duplicate_name_returns_400_duplicated() throws Exception {
    mockMvc.perform(post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated());
    mockMvc
        .perform(post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.children.sandbox_name_duplicated").exists());
  }

  @Test
  void list_returns_persisted_sandbox() throws Exception {
    mockMvc.perform(post("/api/sandboxes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated());
    mockMvc.perform(get("/api/sandboxes")).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sandbox_name").value("勒索沙箱"));
  }
}
```

> 若项目无 `@IntegrationTest` 元注解，使用 `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc`。

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl veriguard-api -am test -Dtest=SandboxApiIntegrationTest
```

预期：FAIL（端点 URL 由 `SecurityValidationApi` 提供，但 service 拆分后注入会失败；或端点尚未迁出）。

- [ ] **Step 3: 创建 `SandboxApi`**

`veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxApi.java`:
```java
package io.veriguard.rest.security_validation;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.exception.InputValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class SandboxApi {

  public static final String SANDBOXES_URI = "/api/sandboxes";

  private final SandboxService sandboxService;

  public SandboxApi(SandboxService sandboxService) {
    this.sandboxService = sandboxService;
  }

  @PostMapping(SANDBOXES_URI)
  @Operation(summary = "Create a sandbox preset")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<SecurityValidationDtos.SandboxOutput> createSandbox(
      @Valid @RequestBody SandboxInput input) throws InputValidationException {
    SecurityValidationDtos.SandboxOutput sandbox = sandboxService.createSandbox(input);
    return ResponseEntity.created(URI.create(SANDBOXES_URI + "/" + sandbox.id())).body(sandbox);
  }

  @GetMapping(SANDBOXES_URI)
  @Operation(summary = "List sandbox presets")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<SecurityValidationDtos.SandboxOutput> sandboxes() {
    return sandboxService.sandboxes();
  }

  @GetMapping(SANDBOXES_URI + "/{sandboxId}")
  @Operation(summary = "Get a sandbox preset")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public SecurityValidationDtos.SandboxOutput sandbox(
      @PathVariable @NotBlank String sandboxId) {
    return sandboxService.sandbox(sandboxId);
  }

  @PutMapping(SANDBOXES_URI + "/{sandboxId}")
  @Operation(summary = "Update a sandbox preset")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public SecurityValidationDtos.SandboxOutput updateSandbox(
      @PathVariable @NotBlank String sandboxId, @Valid @RequestBody SandboxInput input)
      throws InputValidationException {
    return sandboxService.updateSandbox(sandboxId, input);
  }

  @DeleteMapping(SANDBOXES_URI + "/{sandboxId}")
  @Operation(summary = "Delete a sandbox preset")
  @RBAC(actionPerformed = Action.DELETE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<Void> deleteSandbox(@PathVariable @NotBlank String sandboxId) {
    sandboxService.deleteSandbox(sandboxId);
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 4: 从 `SecurityValidationApi` 移除沙箱端点**

打开 `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SecurityValidationApi.java`，删除：
- 常量 `public static final String SANDBOXES_URI = "/api/sandboxes";`
- 方法 `createSandbox / sandboxes / sandbox / updateSandbox / deleteSandbox`
- 不再使用的 import

保留 `capabilityMatrix / attackCatalog / orchestrationSchema` 三个 GET。

- [ ] **Step 5: 全模块编译并跑测试**

```bash
mvn -pl veriguard-api -am compile
mvn -pl veriguard-api -am test
```

预期：编译通过；`SandboxApiIntegrationTest`、`SandboxServiceTest`、`NotImplementedSandboxDriverTest`、`SandboxIntegrationExceptionMappingTest` 全部 PASS；其余既存测试不受影响。

- [ ] **Step 6: 提交（合并 Task 3–7 一次性入仓）**

```bash
git add veriguard-model veriguard-api/src/main/java/io/veriguard/rest/security_validation \
        veriguard-api/src/test/java/io/veriguard/rest/security_validation
git commit -m "refactor(sandbox): extract SandboxApi/SandboxService and slim SandboxInput

- Drop endpoint/providerType from VeriguardSandbox entity and DTOs
- Remove @AssertTrue/@NotEmpty from SandboxInput; centralize validation in SandboxService
- Allow empty network_rules per spec revision
- Translate DataIntegrityViolation to sandbox_name_duplicated reason_code

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 8: KVM 固化（实体已不持有 providerType，仅校验存量数据）

**Files:** 无代码改动；该任务仅占位以便 Task 9 的迁移正确执行（Task 9 删除列已经使语义生效）。

> 设计决定：spec §1.2 决定 `ProviderType` 字段彻底删除（数据库列 + Java 字段 + 枚举），后端不再持有 KVM 概念。Task 3 已经完成实体侧；Task 9 完成数据库侧；运维不需要再"固化"任何字段。本任务跳过。

- [ ] **Step 1: 在计划上把本 Task 标记为"无操作"**（已经如此）。

---

## Task 9: Flyway 迁移 V4_73 + 集成验证

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/migration/V4_73__Extend_veriguard_sandbox.java`

- [ ] **Step 1: 创建迁移类**

`veriguard-api/src/main/java/io/veriguard/migration/V4_73__Extend_veriguard_sandbox.java`:
```java
package io.veriguard.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_73__Extend_veriguard_sandbox extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE veriguard_sandboxes DROP COLUMN IF EXISTS veriguard_sandbox_endpoint;");
      statement.execute("ALTER TABLE veriguard_sandboxes DROP COLUMN IF EXISTS veriguard_sandbox_provider_type;");
      statement.execute(
          "ALTER TABLE veriguard_sandboxes "
              + "ADD CONSTRAINT uk_veriguard_sandboxes_name UNIQUE (veriguard_sandbox_name);");
    }
  }
}
```

- [ ] **Step 2: 启动应用确认迁移成功**

```bash
mvn -pl veriguard-api spring-boot:run
```

（前提：本地 Postgres 已起，按 `veriguard-dev/docker-compose.yml`。）

预期：日志显示 Flyway 应用 `V4_73__Extend_veriguard_sandbox`；进程不报错；`Ctrl-C` 退出。

- [ ] **Step 3: 用集成测试验证唯一约束已生效**

`SandboxApiIntegrationTest.create_with_duplicate_name_returns_400_duplicated`（已在 Task 7 写过）会因 unique violation 触发 `DataIntegrityViolationException` → 我方转 `sandbox_name_duplicated`。

```bash
mvn -pl veriguard-api -am test -Dtest=SandboxApiIntegrationTest#create_with_duplicate_name_returns_400_duplicated
```

预期：PASS。

- [ ] **Step 4: 提交**

```bash
git add veriguard-api/src/main/java/io/veriguard/migration/V4_73__Extend_veriguard_sandbox.java
git commit -m "feat(sandbox): drop endpoint/provider_type and add name unique constraint (V4_73)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 10: `SandboxScriptExporter` —— iptables 生成

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxScriptExporter.java`
- Test: `veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxScriptExporterTest.java`

- [ ] **Step 1: 写失败测试 `SandboxScriptExporterTest`**

新建 `veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxScriptExporterTest.java`:
```java
package io.veriguard.rest.security_validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.veriguard.database.model.VeriguardSandboxNetworkRule;
import io.veriguard.database.model.VeriguardSandboxNetworkRule.Direction;
import io.veriguard.database.model.VeriguardSandboxNetworkRule.RuleAction;
import java.util.List;
import org.junit.jupiter.api.Test;

class SandboxScriptExporterTest {

  private final SandboxScriptExporter exporter = new SandboxScriptExporter();

  @Test
  void to_iptables_with_empty_rules_returns_header_only_script() {
    String script = exporter.toIptables("勒索沙箱", List.of());

    assertThat(script).startsWith("#!/bin/sh");
    assertThat(script).contains("# 沙箱预设「勒索沙箱」未配置网络访问控制规则。");
    assertThat(script).doesNotContain("iptables -A");
  }

  @Test
  void to_iptables_renders_ingress_before_egress() {
    VeriguardSandboxNetworkRule egress =
        new VeriguardSandboxNetworkRule(Direction.EGRESS, RuleAction.ALLOW, "TCP", "10.0.0.0/8", "443");
    VeriguardSandboxNetworkRule ingress =
        new VeriguardSandboxNetworkRule(Direction.INGRESS, RuleAction.DENY, "TCP", "0.0.0.0/0", "all");

    String script = exporter.toIptables("preset", List.of(egress, ingress));

    int ingressIdx = script.indexOf("INPUT");
    int egressIdx = script.indexOf("OUTPUT");
    assertThat(ingressIdx).isPositive();
    assertThat(egressIdx).isPositive();
    assertThat(ingressIdx).isLessThan(egressIdx);
  }

  @Test
  void to_iptables_handles_icmp_with_no_ports() {
    VeriguardSandboxNetworkRule icmp =
        new VeriguardSandboxNetworkRule(Direction.EGRESS, RuleAction.ALLOW, "ICMP", "10.0.0.0/8", "none");

    String script = exporter.toIptables("preset", List.of(icmp));

    assertThat(script).contains("-p icmp");
    assertThat(script).doesNotContain("--dport");
  }

  @Test
  void to_iptables_quotes_sandbox_name_in_filename_safely() {
    String filename = exporter.iptablesFilename("勒索 \"沙箱\"\\test");

    assertThat(filename).doesNotContain("\"");
    assertThat(filename).doesNotContain("\\");
    assertThat(filename).doesNotContain(" ");
    assertThat(filename).endsWith(".iptables.sh");
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl veriguard-api -am test -Dtest=SandboxScriptExporterTest
```

预期：编译失败。

- [ ] **Step 3: 创建 `SandboxScriptExporter`**

`veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxScriptExporter.java`:
```java
package io.veriguard.rest.security_validation;

import io.veriguard.database.model.VeriguardSandboxNetworkRule;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SandboxScriptExporter {

  public String toIptables(String sandboxName, List<VeriguardSandboxNetworkRule> rules) {
    StringBuilder out = new StringBuilder();
    out.append("#!/bin/sh\n");
    out.append("# 沙箱预设「").append(sandboxName).append("」iptables 规则导出。\n");
    out.append("# 在 CAPEv2 主机以 root 执行。\n\n");

    if (rules.isEmpty()) {
      out.append("# 沙箱预设「").append(sandboxName).append("」未配置网络访问控制规则。\n");
      return out.toString();
    }

    rules.stream()
        .sorted(Comparator.comparing(VeriguardSandboxNetworkRule::direction))
        .forEach(rule -> appendIptablesRule(out, rule));
    return out.toString();
  }

  public String toRoutingConf(String sandboxName, List<VeriguardSandboxNetworkRule> rules) {
    StringBuilder out = new StringBuilder();
    out.append("# 沙箱预设「").append(sandboxName).append("」routing.conf 片段。\n");
    out.append("# 将以下内容追加到 CAPEv2 主机的 conf/routing.conf 并重启沙箱服务。\n\n");
    if (rules.isEmpty()) {
      out.append("# 未配置规则。\n");
      return out.toString();
    }
    for (VeriguardSandboxNetworkRule rule : rules) {
      out.append("# ")
          .append(rule.direction())
          .append(" ")
          .append(rule.action())
          .append(" ")
          .append(rule.protocol())
          .append(" ")
          .append(rule.cidr())
          .append(":")
          .append(rule.ports())
          .append("\n");
    }
    return out.toString();
  }

  public String iptablesFilename(String sandboxName) {
    return safe(sandboxName) + ".iptables.sh";
  }

  public String routingConfFilename(String sandboxName) {
    return safe(sandboxName) + ".routing.conf";
  }

  private static String safe(String sandboxName) {
    return sandboxName.replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5-]+", "_");
  }

  private static void appendIptablesRule(StringBuilder out, VeriguardSandboxNetworkRule rule) {
    String chain = rule.direction() == VeriguardSandboxNetworkRule.Direction.INGRESS ? "INPUT" : "OUTPUT";
    String action = rule.action() == VeriguardSandboxNetworkRule.RuleAction.ALLOW ? "ACCEPT" : "DROP";
    String proto = rule.protocol().toLowerCase();
    out.append("iptables -A ").append(chain);
    out.append(" -s ").append(rule.cidr());
    if (!"icmp".equalsIgnoreCase(proto) && !"all".equalsIgnoreCase(proto)) {
      out.append(" -p ").append(proto);
      if (!"all".equalsIgnoreCase(rule.ports())) {
        out.append(" --dport ").append(rule.ports().replace(',', ','));
      }
    } else if ("icmp".equalsIgnoreCase(proto)) {
      out.append(" -p icmp");
    }
    out.append(" -j ").append(action).append("\n");
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl veriguard-api -am test -Dtest=SandboxScriptExporterTest
```

预期：4 个测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxScriptExporter.java \
        veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxScriptExporterTest.java
git commit -m "feat(sandbox): generate iptables and routing.conf scripts for sandbox presets

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 11: 导出 REST 端点 + 集成测试

**Files:**
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxApi.java`
- Modify: `veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxApiIntegrationTest.java`

- [ ] **Step 1: 在 `SandboxApiIntegrationTest` 追加导出端点用例**

在文件末尾追加：
```java
  @Test
  void export_iptables_returns_text_plain_with_attachment_header() throws Exception {
    String created = mockMvc.perform(post("/api/sandboxes")
            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andReturn().getResponse().getContentAsString();
    String id = objectMapper.readTree(created).get("sandbox_id").asText();

    mockMvc.perform(get("/api/sandboxes/" + id + "/network-rules/exports/iptables"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
        .andExpect(header().string("Content-Disposition",
            org.hamcrest.Matchers.containsString("attachment; filename=\"")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("#!/bin/sh")));
  }

  @Test
  void export_routing_conf_returns_text_plain() throws Exception {
    String created = mockMvc.perform(post("/api/sandboxes")
            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andReturn().getResponse().getContentAsString();
    String id = objectMapper.readTree(created).get("sandbox_id").asText();

    mockMvc.perform(get("/api/sandboxes/" + id + "/network-rules/exports/routing-conf"))
        .andExpect(status().isOk())
        .andExpect(content().string(
            org.hamcrest.Matchers.containsString("routing.conf 片段")));
  }
```

为新引入的 hamcrest 断言追加 imports：
```java
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl veriguard-api -am test -Dtest=SandboxApiIntegrationTest
```

预期：两个新增测试 FAIL（端点未实现）。

- [ ] **Step 3: 在 `SandboxApi` 末尾追加端点**

```java
  private final SandboxScriptExporter scriptExporter;

  // 修改构造器：
  public SandboxApi(SandboxService sandboxService, SandboxScriptExporter scriptExporter) {
    this.sandboxService = sandboxService;
    this.scriptExporter = scriptExporter;
  }

  @GetMapping(value = SANDBOXES_URI + "/{sandboxId}/network-rules/exports/iptables",
      produces = "text/plain;charset=UTF-8")
  @Operation(summary = "Export iptables script for a sandbox preset")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<String> exportIptables(@PathVariable @NotBlank String sandboxId) {
    SecurityValidationDtos.SandboxOutput sandbox = sandboxService.sandbox(sandboxId);
    String body = scriptExporter.toIptables(sandbox.name(), sandbox.networkRules());
    String filename = scriptExporter.iptablesFilename(sandbox.name());
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .body(body);
  }

  @GetMapping(value = SANDBOXES_URI + "/{sandboxId}/network-rules/exports/routing-conf",
      produces = "text/plain;charset=UTF-8")
  @Operation(summary = "Export CAPEv2 routing.conf snippet for a sandbox preset")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<String> exportRoutingConf(@PathVariable @NotBlank String sandboxId) {
    SecurityValidationDtos.SandboxOutput sandbox = sandboxService.sandbox(sandboxId);
    String body = scriptExporter.toRoutingConf(sandbox.name(), sandbox.networkRules());
    String filename = scriptExporter.routingConfFilename(sandbox.name());
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .body(body);
  }
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl veriguard-api -am test -Dtest=SandboxApiIntegrationTest
```

预期：所有用例 PASS。

- [ ] **Step 5: 提交**

```bash
git add veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxApi.java \
        veriguard-api/src/test/java/io/veriguard/rest/security_validation/SandboxApiIntegrationTest.java
git commit -m "feat(sandbox): expose iptables and routing.conf export endpoints

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 12: 前端 actions 收窄 + 导出函数

**Files:**
- Modify: `veriguard-front/src/actions/veriguard/veriguard-actions.ts`

- [ ] **Step 1: 重写沙箱相关 actions 与类型**

打开 `veriguard-front/src/actions/veriguard/veriguard-actions.ts`，修改沙箱部分：

```ts
// 删除：SandboxProviderType 类型定义
// 删除：SandboxInput.sandbox_provider_type / sandbox_endpoint 字段
// 修改：保留剩余 SandboxInput / SandboxOutput 字段

export type SandboxNetworkPolicy = 'DENY_ALL' | 'ALLOWLIST' | 'ISOLATED_LAB' | 'CUSTOM';
export type SandboxSampleType =
  | 'RANSOMWARE' | 'MINER' | 'WORM' | 'MALICIOUS_DRIVER'
  | 'PRIVILEGE_ESCALATION' | 'ACCOUNT_THEFT' | 'PROXY_EXECUTION' | 'SECURITY_COMPONENT_BYPASS';
export type SandboxStatus = 'ACTIVE' | 'INACTIVE';
export type SandboxRuleDirection = 'INGRESS' | 'EGRESS';
export type SandboxRuleAction = 'ALLOW' | 'DENY';

export type SandboxNetworkRule = {
  rule_direction: SandboxRuleDirection;
  rule_action: SandboxRuleAction;
  rule_protocol: string;
  rule_cidr: string;
  rule_ports: string;
};

export type SandboxInput = {
  sandbox_name: string;
  sandbox_description?: string;
  sandbox_network_policy: SandboxNetworkPolicy;
  sandbox_network_rules: SandboxNetworkRule[];
  sandbox_auto_restore_enabled: boolean;
  sandbox_supported_sample_types: SandboxSampleType[];
  sandbox_status: SandboxStatus;
};

export type SandboxOutput = SandboxInput & {
  sandbox_id: string;
  sandbox_created_at: string;
  sandbox_updated_at: string;
};

export const fetchVeriguardSandboxes = async () => {
  const response = await simpleCall(SANDBOXES_URI);
  return response.data as SandboxOutput[];
};

export const createVeriguardSandbox = async (input: SandboxInput) => {
  const response = await simplePostCall(SANDBOXES_URI, input);
  return response.data as SandboxOutput;
};

export const updateVeriguardSandbox = async (sandboxId: string, input: SandboxInput) => {
  const response = await simplePutCall(`${SANDBOXES_URI}/${sandboxId}`, input);
  return response.data as SandboxOutput;
};

export const deleteVeriguardSandbox = async (sandboxId: string) => {
  await simpleDelCall(`${SANDBOXES_URI}/${sandboxId}`);
};

export const exportSandboxIptables = async (sandboxId: string): Promise<{ filename: string; content: string }> => {
  const response = await simpleCall(
    `${SANDBOXES_URI}/${sandboxId}/network-rules/exports/iptables`,
    { responseType: 'text' as const, transformResponse: [(data: string) => data] }
  );
  const cd: string = response.headers['content-disposition'] ?? '';
  const match = /filename="([^"]+)"/.exec(cd);
  return { filename: match?.[1] ?? `sandbox-${sandboxId}.iptables.sh`, content: response.data as string };
};

export const exportSandboxRoutingConf = async (sandboxId: string): Promise<{ filename: string; content: string }> => {
  const response = await simpleCall(
    `${SANDBOXES_URI}/${sandboxId}/network-rules/exports/routing-conf`,
    { responseType: 'text' as const, transformResponse: [(data: string) => data] }
  );
  const cd: string = response.headers['content-disposition'] ?? '';
  const match = /filename="([^"]+)"/.exec(cd);
  return { filename: match?.[1] ?? `sandbox-${sandboxId}.routing.conf`, content: response.data as string };
};
```

- [ ] **Step 2: 跑前端类型检查**

```bash
cd veriguard-front
yarn check-ts
```

预期：FAIL（`VeriguardConsole.tsx` 引用了已删除的 `SandboxProviderType`）。这是预期；Task 17 修复 console；先继续。

> 短期断裂可接受；本计划末尾提交时一致绿。如果偏好不留断裂，先跳到 Task 19 做 console 拆分，再回来继续。

- [ ] **Step 3: 暂不提交**——与 Task 19 合并提交。

---

## Task 13: 前端 CIDR / 端口校验工具

**Files:**
- Create: `veriguard-front/src/admin/components/veriguard/sandbox/utils/cidr-port-validators.ts`
- Test: `veriguard-front/src/admin/components/veriguard/sandbox/utils/__tests__/cidr-port-validators.test.ts`

- [ ] **Step 1: 写失败测试**

`__tests__/cidr-port-validators.test.ts`:
```ts
import { describe, expect, test } from 'vitest';
import { isValidCidr, isValidPortExpression } from '../cidr-port-validators';

describe('isValidCidr', () => {
  test.each([
    ['10.0.0.0/8', true],
    ['0.0.0.0/0', true],
    ['192.168.1.1/32', true],
    ['::/0', true],
    ['fe80::/10', true],
    ['256.0.0.0/8', false],
    ['10.0.0.0/33', false],
    ['hello', false],
    ['', false],
  ])('isValidCidr(%s) -> %s', (input, expected) => {
    expect(isValidCidr(input)).toBe(expected);
  });
});

describe('isValidPortExpression', () => {
  test.each([
    ['all', true],
    ['80', true],
    ['1-65535', true],
    ['80,443', true],
    ['80,443,8080-8090', true],
    ['none', true],
    ['65536', false],
    ['80-79', false],
    ['abc', false],
    ['', false],
  ])('isValidPortExpression(%s) -> %s', (input, expected) => {
    expect(isValidPortExpression(input)).toBe(expected);
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

```bash
yarn vitest run src/admin/components/veriguard/sandbox/utils
```

预期：FAIL（模块不存在）。

- [ ] **Step 3: 实现工具**

`cidr-port-validators.ts`:
```ts
const IPV4_OCTET = '(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)';
const IPV4 = `${IPV4_OCTET}(?:\\.${IPV4_OCTET}){3}`;
const IPV4_CIDR = new RegExp(`^${IPV4}/(?:[0-9]|[12][0-9]|3[0-2])$`);
const IPV6_CIDR = /^([0-9A-Fa-f:]+)\/(?:[0-9]|[1-9][0-9]|1[01][0-9]|12[0-8])$/;

export const isValidCidr = (input: string): boolean => {
  if (!input) return false;
  if (IPV4_CIDR.test(input)) return true;
  if (IPV6_CIDR.test(input)) {
    const [addr] = input.split('/');
    const groups = addr.split(':');
    if (groups.length > 8) return false;
    return groups.every(g => g === '' || /^[0-9A-Fa-f]{1,4}$/.test(g));
  }
  return false;
};

export const isValidPortExpression = (input: string): boolean => {
  if (!input) return false;
  if (input === 'all' || input === 'none') return true;
  return input.split(',').every((part) => {
    const trimmed = part.trim();
    if (/^\d+$/.test(trimmed)) {
      const n = Number(trimmed);
      return n >= 1 && n <= 65535;
    }
    const range = /^(\d+)-(\d+)$/.exec(trimmed);
    if (range) {
      const a = Number(range[1]); const b = Number(range[2]);
      return a >= 1 && a <= 65535 && b >= a && b <= 65535;
    }
    return false;
  });
};
```

- [ ] **Step 4: 跑测试确认通过**

```bash
yarn vitest run src/admin/components/veriguard/sandbox/utils
```

预期：所有 case PASS。

- [ ] **Step 5: 提交**

```bash
git add veriguard-front/src/admin/components/veriguard/sandbox/utils
git commit -m "feat(sandbox): add CIDR and port expression validators

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 14: `DeleteConfirmDialog` 通用组件

**Files:**
- Create: `veriguard-front/src/admin/components/veriguard/sandbox/DeleteConfirmDialog.tsx`

- [ ] **Step 1: 实现**

```tsx
import {
  Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle,
} from '@mui/material';

type Props = {
  open: boolean;
  title: string;
  message: string;
  onCancel: () => void;
  onConfirm: () => void;
};

const DeleteConfirmDialog = ({ open, title, message, onCancel, onConfirm }: Props) => (
  <Dialog open={open} onClose={onCancel}>
    <DialogTitle>{title}</DialogTitle>
    <DialogContent>
      <DialogContentText>{message}</DialogContentText>
    </DialogContent>
    <DialogActions>
      <Button onClick={onCancel}>取消</Button>
      <Button color="error" variant="contained" onClick={onConfirm}>确认删除</Button>
    </DialogActions>
  </Dialog>
);

export default DeleteConfirmDialog;
```

- [ ] **Step 2: 提交（与 Task 15 合并入库前可单独 commit）**

```bash
git add veriguard-front/src/admin/components/veriguard/sandbox/DeleteConfirmDialog.tsx
git commit -m "feat(sandbox): add reusable DeleteConfirmDialog component

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 15: `NetworkRuleEditor` 多规则编辑器 + 测试

**Files:**
- Create: `veriguard-front/src/admin/components/veriguard/sandbox/NetworkRuleEditor.tsx`
- Test: `veriguard-front/src/admin/components/veriguard/sandbox/__tests__/NetworkRuleEditor.test.tsx`

- [ ] **Step 1: 写失败测试**

`__tests__/NetworkRuleEditor.test.tsx`:
```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';
import NetworkRuleEditor from '../NetworkRuleEditor';

describe('NetworkRuleEditor', () => {
  test('renders empty state when no rules', () => {
    render(<NetworkRuleEditor value={[]} onChange={() => {}} />);
    expect(screen.getByText(/尚未配置规则/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /添加规则/ })).toBeEnabled();
  });

  test('clicking add appends a rule', () => {
    const onChange = vi.fn();
    render(<NetworkRuleEditor value={[]} onChange={onChange} />);
    fireEvent.click(screen.getByRole('button', { name: /添加规则/ }));
    expect(onChange).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({
      rule_protocol: 'TCP',
    })]));
  });

  test('removing the last rule yields empty array (allowed)', () => {
    const onChange = vi.fn();
    render(<NetworkRuleEditor
      value={[{ rule_direction: 'EGRESS', rule_action: 'DENY', rule_protocol: 'TCP', rule_cidr: '0.0.0.0/0', rule_ports: 'all' }]}
      onChange={onChange}
    />);
    fireEvent.click(screen.getByRole('button', { name: /删除规则/ }));
    expect(onChange).toHaveBeenCalledWith([]);
  });

  test('ICMP protocol disables port input and forces "none"', () => {
    const onChange = vi.fn();
    render(<NetworkRuleEditor
      value={[{ rule_direction: 'EGRESS', rule_action: 'ALLOW', rule_protocol: 'ICMP', rule_cidr: '10.0.0.0/8', rule_ports: 'none' }]}
      onChange={onChange}
    />);
    expect(screen.getByDisplayValue('none')).toBeDisabled();
  });

  test('invalid CIDR shows inline error', () => {
    render(<NetworkRuleEditor
      value={[{ rule_direction: 'EGRESS', rule_action: 'DENY', rule_protocol: 'TCP', rule_cidr: 'not-a-cidr', rule_ports: '80' }]}
      onChange={() => {}}
    />);
    expect(screen.getByText(/CIDR 格式无效/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

```bash
yarn vitest run src/admin/components/veriguard/sandbox/__tests__/NetworkRuleEditor.test.tsx
```

预期：FAIL（模块不存在）。

- [ ] **Step 3: 实现 `NetworkRuleEditor`**

`NetworkRuleEditor.tsx`:
```tsx
import {
  Box, Button, FormControl, IconButton, InputLabel, MenuItem, Select,
  Stack, TextField, Typography,
} from '@mui/material';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import AddOutlined from '@mui/icons-material/AddOutlined';
import type {
  SandboxNetworkRule, SandboxRuleAction, SandboxRuleDirection,
} from '../../../../actions/veriguard/veriguard-actions';
import { isValidCidr, isValidPortExpression } from './utils/cidr-port-validators';

type Props = {
  value: SandboxNetworkRule[];
  onChange: (next: SandboxNetworkRule[]) => void;
  disabled?: boolean;
};

const PROTOCOLS = ['TCP', 'UDP', 'ICMP', 'ALL'] as const;
const DIRECTIONS: SandboxRuleDirection[] = ['INGRESS', 'EGRESS'];
const ACTIONS: SandboxRuleAction[] = ['ALLOW', 'DENY'];

const defaultRule: SandboxNetworkRule = {
  rule_direction: 'EGRESS',
  rule_action: 'DENY',
  rule_protocol: 'TCP',
  rule_cidr: '0.0.0.0/0',
  rule_ports: 'all',
};

const NetworkRuleEditor = ({ value, onChange, disabled }: Props) => {
  const replace = (idx: number, partial: Partial<SandboxNetworkRule>) => {
    const next = value.map((r, i) => (i === idx ? { ...r, ...partial } : r));
    onChange(next);
  };
  const remove = (idx: number) => onChange(value.filter((_, i) => i !== idx));
  const add = () => onChange([...value, defaultRule]);

  if (value.length === 0) {
    return (
      <Box sx={{ p: 2, border: '1px dashed', borderColor: 'divider', borderRadius: 1 }}>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          尚未配置规则。沙箱平台主机将沿用默认网络策略。
        </Typography>
        <Button startIcon={<AddOutlined />} onClick={add} disabled={disabled}>
          添加规则
        </Button>
      </Box>
    );
  }

  return (
    <Stack gap={1}>
      {value.map((rule, idx) => {
        const cidrInvalid = !isValidCidr(rule.rule_cidr);
        const portsInvalid = !isValidPortExpression(rule.rule_ports);
        const isIcmp = rule.rule_protocol.toUpperCase() === 'ICMP';
        return (
          <Stack key={idx} direction="row" gap={1} alignItems="center">
            <FormControl size="small" sx={{ minWidth: 100 }}>
              <InputLabel>方向</InputLabel>
              <Select label="方向" value={rule.rule_direction}
                onChange={e => replace(idx, { rule_direction: e.target.value as SandboxRuleDirection })}
                disabled={disabled}>
                {DIRECTIONS.map(d => <MenuItem key={d} value={d}>{d}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 100 }}>
              <InputLabel>动作</InputLabel>
              <Select label="动作" value={rule.rule_action}
                onChange={e => replace(idx, { rule_action: e.target.value as SandboxRuleAction })}
                disabled={disabled}>
                {ACTIONS.map(a => <MenuItem key={a} value={a}>{a}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 100 }}>
              <InputLabel>协议</InputLabel>
              <Select label="协议" value={rule.rule_protocol}
                onChange={e => {
                  const proto = String(e.target.value);
                  if (proto.toUpperCase() === 'ICMP') {
                    replace(idx, { rule_protocol: proto, rule_ports: 'none' });
                  } else {
                    replace(idx, { rule_protocol: proto });
                  }
                }}
                disabled={disabled}>
                {PROTOCOLS.map(p => <MenuItem key={p} value={p}>{p}</MenuItem>)}
              </Select>
            </FormControl>
            <TextField size="small" label="CIDR" value={rule.rule_cidr}
              onChange={e => replace(idx, { rule_cidr: e.target.value })}
              error={cidrInvalid} helperText={cidrInvalid ? 'CIDR 格式无效' : undefined}
              disabled={disabled} />
            <TextField size="small" label="端口" value={rule.rule_ports}
              onChange={e => replace(idx, { rule_ports: e.target.value })}
              error={portsInvalid} helperText={portsInvalid ? '端口表达式无效' : undefined}
              disabled={disabled || isIcmp} />
            <IconButton onClick={() => remove(idx)} aria-label="删除规则" disabled={disabled}>
              <DeleteOutline />
            </IconButton>
          </Stack>
        );
      })}
      <Button startIcon={<AddOutlined />} onClick={add} disabled={disabled} sx={{ alignSelf: 'flex-start' }}>
        添加规则
      </Button>
    </Stack>
  );
};

export default NetworkRuleEditor;
```

- [ ] **Step 4: 跑测试确认通过**

```bash
yarn vitest run src/admin/components/veriguard/sandbox/__tests__/NetworkRuleEditor.test.tsx
```

预期：5 个测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add veriguard-front/src/admin/components/veriguard/sandbox/NetworkRuleEditor.tsx \
        veriguard-front/src/admin/components/veriguard/sandbox/__tests__/NetworkRuleEditor.test.tsx
git commit -m "feat(sandbox): NetworkRuleEditor with empty-state and inline validation

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 16: `SandboxDialog`（新建/编辑表单）+ 测试

**Files:**
- Create: `veriguard-front/src/admin/components/veriguard/sandbox/SandboxDialog.tsx`
- Test: `veriguard-front/src/admin/components/veriguard/sandbox/__tests__/SandboxDialog.test.tsx`

- [ ] **Step 1: 写失败测试**

`__tests__/SandboxDialog.test.tsx`:
```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';
import SandboxDialog from '../SandboxDialog';

const baseValue = {
  sandbox_name: '勒索沙箱',
  sandbox_description: '',
  sandbox_network_policy: 'DENY_ALL' as const,
  sandbox_network_rules: [],
  sandbox_auto_restore_enabled: true,
  sandbox_supported_sample_types: ['RANSOMWARE' as const],
  sandbox_status: 'ACTIVE' as const,
};

describe('SandboxDialog', () => {
  test('save button enabled when network rules empty (修订点)', () => {
    render(<SandboxDialog open mode="create" initialValue={baseValue}
      onCancel={() => {}} onSubmit={() => {}} />);
    expect(screen.getByRole('button', { name: /保存/ })).toBeEnabled();
  });

  test('save button disabled when name blank', () => {
    render(<SandboxDialog open mode="create"
      initialValue={{ ...baseValue, sandbox_name: '' }}
      onCancel={() => {}} onSubmit={() => {}} />);
    expect(screen.getByRole('button', { name: /保存/ })).toBeDisabled();
  });

  test('save button disabled when no sample types', () => {
    render(<SandboxDialog open mode="create"
      initialValue={{ ...baseValue, sandbox_supported_sample_types: [] }}
      onCancel={() => {}} onSubmit={() => {}} />);
    expect(screen.getByRole('button', { name: /保存/ })).toBeDisabled();
  });

  test('save button disabled when auto restore off', () => {
    render(<SandboxDialog open mode="create"
      initialValue={{ ...baseValue, sandbox_auto_restore_enabled: false }}
      onCancel={() => {}} onSubmit={() => {}} />);
    expect(screen.getByRole('button', { name: /保存/ })).toBeDisabled();
  });

  test('submit returns the current form value', () => {
    const onSubmit = vi.fn();
    render(<SandboxDialog open mode="create" initialValue={baseValue}
      onCancel={() => {}} onSubmit={onSubmit} />);
    fireEvent.click(screen.getByRole('button', { name: /保存/ }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ sandbox_name: '勒索沙箱' }));
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

```bash
yarn vitest run src/admin/components/veriguard/sandbox/__tests__/SandboxDialog.test.tsx
```

预期：FAIL（组件不存在）。

- [ ] **Step 3: 实现 `SandboxDialog`**

```tsx
import {
  Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControl, FormControlLabel, InputLabel, MenuItem, Select, Stack, Switch,
  TextField, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import type {
  SandboxInput, SandboxNetworkPolicy, SandboxSampleType, SandboxStatus,
} from '../../../../actions/veriguard/veriguard-actions';
import NetworkRuleEditor from './NetworkRuleEditor';

const NETWORK_POLICIES: SandboxNetworkPolicy[] = ['DENY_ALL', 'ALLOWLIST', 'ISOLATED_LAB', 'CUSTOM'];
const SAMPLE_TYPES: SandboxSampleType[] = [
  'RANSOMWARE', 'MINER', 'WORM', 'MALICIOUS_DRIVER',
  'PRIVILEGE_ESCALATION', 'ACCOUNT_THEFT', 'PROXY_EXECUTION', 'SECURITY_COMPONENT_BYPASS',
];
const SAMPLE_TYPE_LABELS: Record<SandboxSampleType, string> = {
  RANSOMWARE: '勒索病毒样本执行',
  MINER: '挖矿病毒样本执行',
  WORM: '蠕虫病毒样本执行',
  MALICIOUS_DRIVER: '终端恶意驱动加载',
  PRIVILEGE_ESCALATION: '终端权限提升',
  ACCOUNT_THEFT: '终端系统账号窃取',
  PROXY_EXECUTION: '终端代理执行',
  SECURITY_COMPONENT_BYPASS: '终端安全组件对抗',
};
const STATUS_TYPES: SandboxStatus[] = ['ACTIVE', 'INACTIVE'];

type Props = {
  open: boolean;
  mode: 'create' | 'edit';
  initialValue: SandboxInput;
  onCancel: () => void;
  onSubmit: (value: SandboxInput) => void;
};

const SandboxDialog = ({ open, mode, initialValue, onCancel, onSubmit }: Props) => {
  const [form, setForm] = useState<SandboxInput>(initialValue);
  useEffect(() => { setForm(initialValue); }, [initialValue]);

  const formValid = useMemo(() => (
    form.sandbox_name.trim().length > 0
      && form.sandbox_supported_sample_types.length > 0
      && form.sandbox_auto_restore_enabled
  ), [form]);

  const updateField = <K extends keyof SandboxInput>(key: K, value: SandboxInput[K]) => {
    setForm(prev => ({ ...prev, [key]: value }));
  };
  const toggleSampleType = (st: SandboxSampleType) => {
    const exists = form.sandbox_supported_sample_types.includes(st);
    updateField('sandbox_supported_sample_types',
      exists ? form.sandbox_supported_sample_types.filter(s => s !== st)
             : [...form.sandbox_supported_sample_types, st]);
  };

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="md" fullWidth>
      <DialogTitle>{mode === 'create' ? '新建沙箱预设' : '编辑沙箱预设'}</DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 2 }}>
          网络访问控制策略与自动还原快照由沙箱平台主机管理员负责实际生效。本系统仅持久化策略并提供导出脚本，对快照配置缺失给出可视化告警；不参与运行时下发与执行。
        </Alert>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, pt: 1 }}>
          <TextField label="名称" value={form.sandbox_name}
            onChange={e => updateField('sandbox_name', e.target.value)} required />
          <FormControl>
            <InputLabel>网络策略</InputLabel>
            <Select label="网络策略" value={form.sandbox_network_policy}
              onChange={e => updateField('sandbox_network_policy', e.target.value as SandboxNetworkPolicy)}>
              {NETWORK_POLICIES.map(p => <MenuItem key={p} value={p}>{p}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField label="描述" value={form.sandbox_description ?? ''}
            onChange={e => updateField('sandbox_description', e.target.value)}
            multiline minRows={2} sx={{ gridColumn: '1 / -1' }} />
          <Box sx={{ gridColumn: '1 / -1' }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>网络访问控制规则（可选）</Typography>
            <NetworkRuleEditor value={form.sandbox_network_rules}
              onChange={v => updateField('sandbox_network_rules', v)} />
          </Box>
          <FormControlLabel
            control={(
              <Switch checked={form.sandbox_auto_restore_enabled}
                onChange={e => updateField('sandbox_auto_restore_enabled', e.target.checked)} />
            )}
            label="执行完成后自动还原" />
          <FormControl>
            <InputLabel>状态</InputLabel>
            <Select label="状态" value={form.sandbox_status}
              onChange={e => updateField('sandbox_status', e.target.value as SandboxStatus)}>
              {STATUS_TYPES.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
            </Select>
          </FormControl>
          <Box sx={{ gridColumn: '1 / -1' }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>样本类型</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              {SAMPLE_TYPES.map(st => (
                <FormControlLabel key={st}
                  control={(
                    <Checkbox checked={form.sandbox_supported_sample_types.includes(st)}
                      onChange={() => toggleSampleType(st)} />
                  )}
                  label={SAMPLE_TYPE_LABELS[st]} />
              ))}
            </Stack>
          </Box>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>取消</Button>
        <Button variant="contained" disabled={!formValid} onClick={() => onSubmit(form)}>保存</Button>
      </DialogActions>
    </Dialog>
  );
};

export default SandboxDialog;
```

- [ ] **Step 4: 跑测试确认通过**

```bash
yarn vitest run src/admin/components/veriguard/sandbox/__tests__/SandboxDialog.test.tsx
```

预期：5 个测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add veriguard-front/src/admin/components/veriguard/sandbox/SandboxDialog.tsx \
        veriguard-front/src/admin/components/veriguard/sandbox/__tests__/SandboxDialog.test.tsx
git commit -m "feat(sandbox): SandboxDialog form with boundary banner and validation

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 17: `SandboxList` + 列表筛选 + 行操作菜单

**Files:**
- Create: `veriguard-front/src/admin/components/veriguard/sandbox/SandboxList.tsx`

- [ ] **Step 1: 实现 `SandboxList`**

```tsx
import {
  AddOutlined, MoreVertOutlined,
} from '@mui/icons-material';
import {
  Box, Button, Chip, FormControl, IconButton, InputLabel, MenuItem,
  Menu, Paper, Select, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import {
  createVeriguardSandbox, deleteVeriguardSandbox, exportSandboxIptables,
  exportSandboxRoutingConf, fetchVeriguardSandboxes, updateVeriguardSandbox,
  type SandboxInput, type SandboxOutput, type SandboxSampleType, type SandboxStatus,
} from '../../../../actions/veriguard/veriguard-actions';
import SandboxDialog from './SandboxDialog';
import DeleteConfirmDialog from './DeleteConfirmDialog';

const STATUS_TYPES: SandboxStatus[] = ['ACTIVE', 'INACTIVE'];
const SAMPLE_TYPES: SandboxSampleType[] = [
  'RANSOMWARE', 'MINER', 'WORM', 'MALICIOUS_DRIVER',
  'PRIVILEGE_ESCALATION', 'ACCOUNT_THEFT', 'PROXY_EXECUTION', 'SECURITY_COMPONENT_BYPASS',
];

const downloadText = (filename: string, content: string) => {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = filename; a.click();
  URL.revokeObjectURL(url);
};

const emptyInput: SandboxInput = {
  sandbox_name: '', sandbox_description: '',
  sandbox_network_policy: 'DENY_ALL', sandbox_network_rules: [],
  sandbox_auto_restore_enabled: true, sandbox_supported_sample_types: ['RANSOMWARE'],
  sandbox_status: 'ACTIVE',
};

const SandboxList = () => {
  const [sandboxes, setSandboxes] = useState<SandboxOutput[]>([]);
  const [statusFilter, setStatusFilter] = useState<SandboxStatus | 'ALL'>('ALL');
  const [sampleFilter, setSampleFilter] = useState<SandboxSampleType | 'ALL'>('ALL');
  const [dialogState, setDialogState] = useState<{ open: boolean; mode: 'create' | 'edit'; id?: string; value: SandboxInput }>({
    open: false, mode: 'create', value: emptyInput,
  });
  const [deleteState, setDeleteState] = useState<{ open: boolean; id?: string; name?: string }>({ open: false });
  const [menuAnchor, setMenuAnchor] = useState<{ el: HTMLElement; sandbox: SandboxOutput } | null>(null);

  const reload = async () => setSandboxes(await fetchVeriguardSandboxes());
  useEffect(() => { void reload(); }, []);

  const filtered = useMemo(() => sandboxes.filter(s => (
    (statusFilter === 'ALL' || s.sandbox_status === statusFilter)
      && (sampleFilter === 'ALL' || s.sandbox_supported_sample_types.includes(sampleFilter))
  )), [sandboxes, statusFilter, sampleFilter]);

  const onSubmit = async (value: SandboxInput) => {
    if (dialogState.mode === 'edit' && dialogState.id) {
      await updateVeriguardSandbox(dialogState.id, value);
    } else {
      await createVeriguardSandbox(value);
    }
    setDialogState(s => ({ ...s, open: false }));
    await reload();
  };

  const onDelete = async () => {
    if (deleteState.id) {
      await deleteVeriguardSandbox(deleteState.id);
      setDeleteState({ open: false });
      await reload();
    }
  };

  const onExportIptables = async (id: string) => {
    const { filename, content } = await exportSandboxIptables(id);
    downloadText(filename, content);
  };
  const onExportRoutingConf = async (id: string) => {
    const { filename, content } = await exportSandboxRoutingConf(id);
    downloadText(filename, content);
  };

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h6">沙箱预设</Typography>
        <Stack direction="row" gap={1}>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>状态</InputLabel>
            <Select label="状态" value={statusFilter}
              onChange={e => setStatusFilter(e.target.value as SandboxStatus | 'ALL')}>
              <MenuItem value="ALL">全部</MenuItem>
              {STATUS_TYPES.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel>样本类型</InputLabel>
            <Select label="样本类型" value={sampleFilter}
              onChange={e => setSampleFilter(e.target.value as SandboxSampleType | 'ALL')}>
              <MenuItem value="ALL">全部</MenuItem>
              {SAMPLE_TYPES.map(t => <MenuItem key={t} value={t}>{t}</MenuItem>)}
            </Select>
          </FormControl>
          <Button startIcon={<AddOutlined />} variant="contained"
            onClick={() => setDialogState({ open: true, mode: 'create', value: emptyInput })}>
            新建预设
          </Button>
        </Stack>
      </Stack>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>名称</TableCell>
            <TableCell>规则数</TableCell>
            <TableCell>样本数</TableCell>
            <TableCell>自动还原</TableCell>
            <TableCell>状态</TableCell>
            <TableCell align="right">操作</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {filtered.map(s => (
            <TableRow key={s.sandbox_id}>
              <TableCell>{s.sandbox_name}</TableCell>
              <TableCell>{s.sandbox_network_rules.length}</TableCell>
              <TableCell>{s.sandbox_supported_sample_types.length}</TableCell>
              <TableCell>
                <Chip size="small"
                  color={s.sandbox_auto_restore_enabled ? 'success' : 'error'}
                  label={s.sandbox_auto_restore_enabled ? '已开启' : '未开启'} />
              </TableCell>
              <TableCell>{s.sandbox_status}</TableCell>
              <TableCell align="right">
                <IconButton aria-label={`沙箱「${s.sandbox_name}」操作`}
                  onClick={e => setMenuAnchor({ el: e.currentTarget, sandbox: s })}>
                  <MoreVertOutlined />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Menu open={!!menuAnchor} anchorEl={menuAnchor?.el} onClose={() => setMenuAnchor(null)}>
        {menuAnchor && [
          <MenuItem key="edit" onClick={() => {
            const s = menuAnchor.sandbox;
            setDialogState({ open: true, mode: 'edit', id: s.sandbox_id, value: {
              sandbox_name: s.sandbox_name,
              sandbox_description: s.sandbox_description ?? '',
              sandbox_network_policy: s.sandbox_network_policy,
              sandbox_network_rules: s.sandbox_network_rules,
              sandbox_auto_restore_enabled: s.sandbox_auto_restore_enabled,
              sandbox_supported_sample_types: s.sandbox_supported_sample_types,
              sandbox_status: s.sandbox_status,
            }});
            setMenuAnchor(null);
          }}>编辑</MenuItem>,
          <MenuItem key="iptables" onClick={() => { void onExportIptables(menuAnchor.sandbox.sandbox_id); setMenuAnchor(null); }}>
            导出 iptables 脚本
          </MenuItem>,
          <MenuItem key="routing" onClick={() => { void onExportRoutingConf(menuAnchor.sandbox.sandbox_id); setMenuAnchor(null); }}>
            导出 routing.conf 片段
          </MenuItem>,
          <MenuItem key="delete" onClick={() => {
            setDeleteState({ open: true, id: menuAnchor.sandbox.sandbox_id, name: menuAnchor.sandbox.sandbox_name });
            setMenuAnchor(null);
          }}>删除</MenuItem>,
        ]}
      </Menu>

      <SandboxDialog open={dialogState.open} mode={dialogState.mode}
        initialValue={dialogState.value}
        onCancel={() => setDialogState(s => ({ ...s, open: false }))}
        onSubmit={onSubmit} />
      <DeleteConfirmDialog open={deleteState.open}
        title="删除沙箱预设" message={`确定删除沙箱预设「${deleteState.name ?? ''}」？此操作不可恢复。`}
        onCancel={() => setDeleteState({ open: false })} onConfirm={onDelete} />
    </Paper>
  );
};

export default SandboxList;
```

- [ ] **Step 2: 提交（保留——与 Task 19 合并）**

不在本 Task 单独 commit，待 Task 19 console wire-up 时一并 commit。

---

## Task 18: 跳过（导出按钮已合入 Task 17 行操作菜单）

行操作菜单里已经有"导出 iptables 脚本"与"导出 routing.conf 片段"两项，无需独立 `NetworkRuleExportButtons` 组件。

- [ ] **Step 1: 删除文件影响清单中的 `NetworkRuleExportButtons.tsx` 条目（计划自身的清单也对齐）。**

不创建该文件。

---

## Task 19: 接入 `VeriguardConsole` 沙箱 Tab + 整体编译

**Files:**
- Modify: `veriguard-front/src/admin/components/veriguard/VeriguardConsole.tsx`

- [ ] **Step 1: 修改沙箱 Tab 渲染逻辑**

在 `VeriguardConsole.tsx` 中：

1. **删除** 与沙箱表单 / 对话框 / 删除按钮相关的 state 与 handlers（`dialogOpen` / `editingSandboxId` / `form` / `selectedRule` / `defaultSandboxInput` / `openCreateDialog` / `openEditDialog` / `updateField` / `updateRuleField` / `toggleSampleType` / `submitSandbox` / `removeSandbox` / 整个 `Dialog`、`PROVIDER_TYPES`、`sampleTypeLabels` 等本组件内的常量）。
2. **删除** 不再需要的 imports（`Dialog`、`DialogActions` 等沙箱表单组件 + `SandboxProviderType`）。
3. **保留** 矩阵 / 用例目录 / 攻击编排三个 Tab 的渲染。
4. **沙箱 Tab** 替换为：

```tsx
{tab === 'sandboxes' && <SandboxList />}
```

5. import 顶部加：

```tsx
import SandboxList from './sandbox/SandboxList';
```

6. `loadData` 中删除 `fetchVeriguardSandboxes()` 与 `setSandboxes`（移交给 `SandboxList` 自己管理）。

最终 `VeriguardConsole.tsx` 行数应回落到 ~250 行以内（原 600+ 行）。

- [ ] **Step 2: 跑前端类型检查与单元测试**

```bash
cd veriguard-front
yarn check-ts && yarn lint && yarn test --coverage
```

预期：均 PASS；新组件覆盖率 ≥ 85%（spec §9.6）。如个别测试因 MUI 异步渲染时序失败，加 `@testing-library/react` 的 `findBy*` 替换 `getBy*`。

- [ ] **Step 3: 提交（合并 Task 12 / 14 / 17 / 19 的所有前端变更）**

```bash
git add veriguard-front/src/actions/veriguard/veriguard-actions.ts \
        veriguard-front/src/admin/components/veriguard/VeriguardConsole.tsx \
        veriguard-front/src/admin/components/veriguard/sandbox
git commit -m "feat(sandbox): rebuild console sandbox tab with list/dialog/export

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 20: Playwright E2E `sandbox.m1`

**Files:**
- Create: `veriguard-front/tests_e2e/admin/veriguard/sandbox/m1.spec.ts`

- [ ] **Step 1: 撰写 E2E 主路径**

```ts
import { expect, test } from '@playwright/test';

test.describe('sandbox m1', () => {
  test('create / edit / export / delete preset', async ({ page }) => {
    await page.goto('/admin/veriguard');
    await page.getByRole('tab', { name: /沙箱管理/ }).click();

    // 新建预设（不填规则）
    await page.getByRole('button', { name: /新建预设/ }).click();
    await page.getByLabel('名称').fill('e2e-勒索沙箱');
    await page.getByRole('checkbox', { name: '勒索病毒样本执行' }).check();
    await page.getByRole('button', { name: '保存' }).click();
    await expect(page.getByText('e2e-勒索沙箱')).toBeVisible();

    // 行操作 → 编辑加规则
    await page.getByRole('button', { name: /沙箱「e2e-勒索沙箱」操作/ }).click();
    await page.getByRole('menuitem', { name: '编辑' }).click();
    await page.getByRole('button', { name: /添加规则/ }).click();
    await page.getByRole('button', { name: '保存' }).click();

    // 导出 iptables（拦截 download 事件）
    await page.getByRole('button', { name: /沙箱「e2e-勒索沙箱」操作/ }).click();
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('menuitem', { name: '导出 iptables 脚本' }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/iptables\.sh$/);

    // 删除（二次确认）
    await page.getByRole('button', { name: /沙箱「e2e-勒索沙箱」操作/ }).click();
    await page.getByRole('menuitem', { name: '删除' }).click();
    await expect(page.getByText('确定删除沙箱预设「e2e-勒索沙箱」')).toBeVisible();
    await page.getByRole('button', { name: '确认删除' }).click();
    await expect(page.getByText('e2e-勒索沙箱')).toHaveCount(0);
  });
});
```

- [ ] **Step 2: 跑 E2E（前提：本地起后端 + 前端 + Postgres + 已应用 V4_73 迁移）**

```bash
cd veriguard-front
yarn test:e2e -g "sandbox m1"
```

预期：单 worker 内 PASS。

- [ ] **Step 3: 提交**

```bash
git add veriguard-front/tests_e2e/admin/veriguard/sandbox/m1.spec.ts
git commit -m "test(sandbox): add Playwright e2e for sandbox preset CRUD and export

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 21: 同步 `Veriguard二开落地说明.md`

**Files:**
- Modify: `docs/参考资料/Veriguard二开落地说明.md`

- [ ] **Step 1: 更新落地路径表**

在"二、落地路径"表追加（或替换）以下条目：

| 类型 | 路径 | 说明 |
| --- | --- | --- |
| 后端驱动 | `veriguard-api/src/main/java/io/veriguard/integration/sandbox/` | 新增 `SandboxDriver` 接口、`NotImplementedSandboxDriver` 占位、`SandboxIntegrationException` |
| 后端服务 | `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxService.java` | 沙箱预设 CRUD 与校验，从 `SecurityValidationService` 拆出 |
| 后端导出 | `veriguard-api/src/main/java/io/veriguard/rest/security_validation/SandboxScriptExporter.java` | iptables / routing.conf 脚本生成 |
| 数据迁移 | `veriguard-api/src/main/java/io/veriguard/migration/V4_73__Extend_veriguard_sandbox.java` | 删除 endpoint / provider_type 列，添加 name 唯一索引 |
| 前端组件 | `veriguard-front/src/admin/components/veriguard/sandbox/` | `SandboxList` / `SandboxDialog` / `NetworkRuleEditor` / `DeleteConfirmDialog` |

- [ ] **Step 2: 更新接口清单**

在"四、接口清单"加：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/sandboxes/{id}/network-rules/exports/iptables` | 导出沙箱网络规则的 iptables 脚本（text/plain，含 Content-Disposition） |
| `GET` | `/api/sandboxes/{id}/network-rules/exports/routing-conf` | 导出沙箱网络规则的 CAPEv2 routing.conf 片段 |

- [ ] **Step 3: 更新检查记录**

在"六、检查记录"追加：

| 命令 | 结果 |
| --- | --- |
| `mvn -pl veriguard-api -am test` | M1 全部 PASS |
| `cd veriguard-front && yarn check-ts && yarn lint && yarn test --coverage` | M1 全部 PASS，沙箱组件覆盖率 ≥ 85% |
| `cd veriguard-front && yarn test:e2e -g "sandbox m1"` | E2E PASS |

- [ ] **Step 4: 单独提交（按 AGENTS.md "实现 + 文档分别 commit"）**

```bash
git add docs/参考资料/Veriguard二开落地说明.md
git commit -m "文档：同步沙箱预设 M1 落地说明

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 22: 终态自检 + 完成定义验证

**Files:** 无（仅运行命令）

- [ ] **Step 1: spotless / lint 全跑**

```bash
mvn -pl veriguard-api -am spotless:apply
git diff --exit-code   # 应无差异
cd veriguard-front && yarn lint --max-warnings 0 && yarn check-ts
```

- [ ] **Step 2: 全模块测试 + 覆盖率**

```bash
mvn -pl veriguard-api -am test
mvn -pl veriguard-api jacoco:report
# 浏览器打开 veriguard-api/target/site/jacoco/index.html 检查
# - io.veriguard.integration.sandbox.** ≥ 90% lines
# - io.veriguard.rest.security_validation.**（新/改） ≥ 85% lines
cd veriguard-front && yarn test --coverage
# 检查 admin/components/veriguard/sandbox/** ≥ 85% lines
```

- [ ] **Step 3: "无 fallback" 自检**

```bash
rg -n 'try.*catch.*\{\s*\}|catch.*Exception.*log\.warn|return.*null.*//.*fallback' \
   veriguard-api/src/main/java/io/veriguard/{integration/sandbox,rest/security_validation,rest/helper}
```

预期：仅 `RestBehavior.handleSandboxIntegrationException` 中的 `log.warn` 命中（这是合规日志，不是 fallback 静默吞错）；其它零命中。如有意外命中，修复并补单测。

- [ ] **Step 4: AGENTS.md 文档校验**

```bash
git diff --check -- docs AGENTS.md
rg -n 'TODO|FIXME|待补充' docs/参考资料/Veriguard二开落地说明.md
```

预期：均无输出。

- [ ] **Step 5: PRD 截图清单**

人工拍以下截图，存到 PR 描述（或 `docs/prd/截图/` 暂存目录）：

1. 沙箱列表 + 状态/样本类型筛选下拉打开。
2. 新建预设对话框（含横幅边界声明）。
3. 编辑对话框 + 多规则编辑器（≥2 条规则）。
4. 删除二次确认对话框。
5. 行操作菜单展开，"导出 iptables 脚本" / "导出 routing.conf 片段"可见。
6. 实际下载的 iptables.sh 文本预览。

- [ ] **Step 6: 合并到 main**

```bash
cd /Users/lamba/github/Veriguard
git checkout main
git merge --no-ff feature/sandbox-m1
git worktree remove worktrees/sandbox-m1
git branch -d feature/sandbox-m1
```

提示：合并 commit 信息按 AGENTS.md 用 Chinese style，例如 `合并：沙箱预设 M1`。

---

## 自检（plan 自身）

**Spec 覆盖度**：
- §1.2 范围 → Task 1–22 完整覆盖（驱动接口、Service/Api 拆分、导出脚本、前端表单、E2E、文档）。
- §4.2 V4_73 迁移 → Task 9。
- §5.1 驱动接口 → Task 1。
- §5.3 Service 拆分 → Task 6。
- §5.4 API 端点 → Task 7、Task 11。
- §5.5 DTO 调整（含 Bean 校验注解策略）→ Task 4 / 5。
- §6.2 NetworkRuleEditor 空态 → Task 15 测试 1。
- §6.6 不引新依赖 → 沿用 useState + vitest，无新包。
- §7.2 CRUD 数据流 → Task 7 集成测试。
- §8.2 错误码 → 落地为 `ValidationErrorBag.errors.children.<reason_code>` 形式（spec §8 已同步修订）。
- §9.2/§9.4 测试 → Task 1/6/10/11/15/16。
- §10.1 M1 交付物 → 全部 Task 已枚举。
- §10.5 完成定义 → Task 22。

**占位符扫描**：无 TBD / TODO / "fill in details" 等。

**类型一致性**：
- `SandboxIntegrationException.ReasonCode` 在 Task 1 与 Task 2 用法一致。
- `SandboxInput` 在 Task 4 / 5 / 6 / 7 / 12 / 16 字段集合一致（前后端镜像）。
- `SandboxOutput` 同上。
- 端点路径 `/api/sandboxes/{id}/network-rules/exports/iptables` 在 Task 11 与 Task 17 / 21 一致。
- `safe(sandboxName)` filename 算法允许中文字符（Task 10），与 e2e 用例 "e2e-勒索沙箱" 兼容。
