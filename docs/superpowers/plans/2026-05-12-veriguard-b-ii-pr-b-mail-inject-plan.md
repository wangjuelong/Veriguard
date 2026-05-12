# B-ii PR-B Implementation Plan — 邮件 Inject + SMTP Profile 管理

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地邮件 inject 类型 — 用户构造钓鱼 / 通知 / 攻击邮件，平台后端通过用户配置的 SMTP profile 发送给目标团队收件人，验证邮件网关 / 反钓鱼系统的检测拦截能力。

**Architecture:** 新建 `injectors/email/EmailContract` (Contractor 元数据) + `EmailExecutor` (NodeExecutor 执行逻辑) + `EmailContent` (Jackson 数据模型) + `MailInjector` 服务（用 jakarta.mail 直接构造 Session/Transport，独立于现有 SmtpService — 后者服务于平台通知邮件不适合做多 profile）+ 新 `SmtpProfile` 实体 + REST CRUD + 测试连通性。Integration 模式参考 ManualNodeExecutorIntegration / Factory 注册到 NodeExecutorService。前端 SMTP profile 管理 + 邮件 inject 节点画布图标。

**Tech Stack:** Spring Boot 3.3.7 / Java 21 / JPA / Flyway / jakarta.mail (spring-boot-starter-mail 已在 pom) / React 19 / TypeScript / MUI

---

## 准备工作

**Worktree（执行前必做）：**

```bash
cd /Users/lamba/github/Veriguard
git fetch origin main
git worktree add worktrees/b-ii-pr-b-mail-inject -b feat/b-ii-pr-b-mail-inject origin/main
cd worktrees/b-ii-pr-b-mail-inject/veriguard-front
yarn install --immutable
```

**环境变量（每个 mvn 命令前都要 export）：**

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

---

## Task 1：V8 Flyway migration — `smtp_profiles` 表

**Why first:** 后续 entity 字段读写依赖 DB 表存在。当前 main 最新 migration 是 V7，新加从 V8 起。

**Files:**

- Create: `veriguard-api/src/main/resources/db/migration/V8__smtp_profiles.sql`

### Steps

- [ ] **Step 1.1：创建 V8 migration 文件**

```sql
-- V8: B-ii PR-B Mail Inject — SMTP Profile 表
--
-- SMTP profile 由运维人员在平台界面配置，承载发送邮件 inject 所需的 SMTP 服务器信息.
-- Email inject 执行时通过 smtp_profile_id 选择具体 profile.
-- 密码字段以加密形式存储（由 Veriguard 现有加密层处理，本表只存字符串列）.

CREATE TABLE smtp_profiles (
    smtp_profile_id          VARCHAR(255) PRIMARY KEY,
    smtp_profile_name        VARCHAR(255) NOT NULL,
    smtp_profile_host        VARCHAR(255) NOT NULL,
    smtp_profile_port        INTEGER NOT NULL,
    smtp_profile_auth_type   VARCHAR(32) NOT NULL DEFAULT 'NONE',
    smtp_profile_username    VARCHAR(255),
    smtp_profile_password    TEXT,
    smtp_profile_tls_mode    VARCHAR(32) NOT NULL DEFAULT 'STARTTLS',
    smtp_profile_default_from VARCHAR(255) NOT NULL,
    smtp_profile_default_reply_to VARCHAR(255),
    smtp_profile_created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    smtp_profile_updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_smtp_profiles_name ON smtp_profiles (smtp_profile_name);
```

- [ ] **Step 1.2：跑后端 compile 触发 Flyway 校验**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-b-mail-inject
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 1.3：跑现有测试验证 Flyway 应用 V8 OK**

```bash
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'BUILD SUCCESS|BUILD FAILURE|Tests run' | head -5
```

Expected: `Tests run: 18, Failures: 0, Errors: 0` + BUILD SUCCESS.

- [ ] **Step 1.4：Commit**

```bash
git add veriguard-api/src/main/resources/db/migration/V8__smtp_profiles.sql
git commit -m "$(cat <<'EOF'
执行：V8 Flyway 加 smtp_profiles 表（B-ii PR-B Step 1）

承载邮件 inject 发送所需的 SMTP 服务器配置，运维人员通过平台界面 CRUD 管理：
- host / port / auth_type / username / password / tls_mode
- default_from / default_reply_to 作为发件人默认值（inject 可覆盖）
- UNIQUE INDEX 防止重名 profile

后续 SmtpProfile entity 映射此表，MailInjector 根据 profile 构造 jakarta.mail Session.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2：SmtpProfile entity + Repository

**Files:**

- Create: `veriguard-model/src/main/java/io/veriguard/database/model/SmtpProfile.java`
- Create: `veriguard-model/src/main/java/io/veriguard/database/repository/SmtpProfileRepository.java`

### Steps

- [ ] **Step 2.1：读 Agent.java 作为 entity 风格参考**

```bash
head -60 veriguard-model/src/main/java/io/veriguard/database/model/Agent.java
```

注意 `@Getter @Setter @Entity @Table @EntityListeners(ModelBaseListener.class)` 模式。

- [ ] **Step 2.2：创建 SmtpProfile.java**

```java
package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "smtp_profiles")
@EntityListeners(ModelBaseListener.class)
public class SmtpProfile implements Base {

  public enum AUTH_TYPE {
    @JsonProperty("none")
    NONE,
    @JsonProperty("password")
    PASSWORD,
  }

  public enum TLS_MODE {
    @JsonProperty("none")
    NONE,
    @JsonProperty("starttls")
    STARTTLS,
    @JsonProperty("tls")
    TLS,
  }

  @Id
  @Column(name = "smtp_profile_id")
  @JsonProperty("smtp_profile_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "smtp_profile_name")
  @JsonProperty("smtp_profile_name")
  @NotBlank
  private String name;

  @Column(name = "smtp_profile_host")
  @JsonProperty("smtp_profile_host")
  @NotBlank
  private String host;

  @Column(name = "smtp_profile_port")
  @JsonProperty("smtp_profile_port")
  @NotNull
  @Min(1)
  private Integer port;

  @Column(name = "smtp_profile_auth_type")
  @JsonProperty("smtp_profile_auth_type")
  @Enumerated(EnumType.STRING)
  @NotNull
  private AUTH_TYPE authType = AUTH_TYPE.NONE;

  @Column(name = "smtp_profile_username")
  @JsonProperty("smtp_profile_username")
  private String username;

  @Column(name = "smtp_profile_password")
  @JsonProperty("smtp_profile_password")
  private String password;

  @Column(name = "smtp_profile_tls_mode")
  @JsonProperty("smtp_profile_tls_mode")
  @Enumerated(EnumType.STRING)
  @NotNull
  private TLS_MODE tlsMode = TLS_MODE.STARTTLS;

  @Column(name = "smtp_profile_default_from")
  @JsonProperty("smtp_profile_default_from")
  @NotBlank
  private String defaultFrom;

  @Column(name = "smtp_profile_default_reply_to")
  @JsonProperty("smtp_profile_default_reply_to")
  private String defaultReplyTo;

  @CreationTimestamp
  @Column(name = "smtp_profile_created_at")
  @JsonProperty("smtp_profile_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "smtp_profile_updated_at")
  @JsonProperty("smtp_profile_updated_at")
  private Instant updatedAt;
}
```

- [ ] **Step 2.3：创建 SmtpProfileRepository.java**

```java
package io.veriguard.database.repository;

import io.veriguard.database.model.SmtpProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SmtpProfileRepository
    extends CrudRepository<SmtpProfile, String>, JpaSpecificationExecutor<SmtpProfile> {

  Optional<SmtpProfile> findByName(String name);
}
```

- [ ] **Step 2.4：编译验证**

```bash
mvn -pl veriguard-model -am compile -DskipTests -q 2>&1 | tail -5
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: BUILD SUCCESS / 18 测试过.

- [ ] **Step 2.5：Commit**

```bash
git add veriguard-model/src/main/java/io/veriguard/database/model/SmtpProfile.java \
        veriguard-model/src/main/java/io/veriguard/database/repository/SmtpProfileRepository.java
git commit -m "$(cat <<'EOF'
执行：SmtpProfile entity + Repository（B-ii PR-B Step 2）

- SmtpProfile entity 映射 smtp_profiles 表，含 host / port / auth_type / TLS mode /
  default_from / default_reply_to 等字段
- AUTH_TYPE / TLS_MODE 枚举（@Enumerated.STRING）
- 自动 created_at / updated_at 时间戳
- Repository 提供基础 CRUD + findByName（name 唯一）

后续 SmtpProfileService 封装业务逻辑，REST API 暴露 CRUD.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3：SmtpProfileService + 7 单测（TDD）

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/service/SmtpProfileService.java`
- Create: `veriguard-api/src/test/java/io/veriguard/service/SmtpProfileServiceTest.java`

### Steps

- [ ] **Step 3.1：先写失败测试**

写入 `veriguard-api/src/test/java/io/veriguard/service/SmtpProfileServiceTest.java`：

```java
package io.veriguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.SmtpProfile;
import io.veriguard.database.repository.SmtpProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmtpProfileServiceTest {

  @Mock private SmtpProfileRepository repository;

  @InjectMocks private SmtpProfileService service;

  private SmtpProfile profile(String name) {
    SmtpProfile p = new SmtpProfile();
    p.setName(name);
    p.setHost("smtp.example.com");
    p.setPort(587);
    p.setDefaultFrom("noreply@example.com");
    return p;
  }

  @Test
  @DisplayName("create 正常 → 保存并返回")
  void create_savesAndReturns() {
    SmtpProfile input = profile("prod-smtp");
    when(repository.findByName("prod-smtp")).thenReturn(Optional.empty());
    when(repository.save(input)).thenReturn(input);

    SmtpProfile result = service.create(input);

    assertThat(result).isSameAs(input);
    verify(repository).save(input);
  }

  @Test
  @DisplayName("create 重名 → 抛 IllegalArgumentException，不调 save")
  void create_duplicateName_throwsAndSkipsSave() {
    SmtpProfile input = profile("dup-smtp");
    when(repository.findByName("dup-smtp")).thenReturn(Optional.of(profile("dup-smtp")));

    assertThrows(IllegalArgumentException.class, () -> service.create(input));
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("findById 命中 → 返回 profile")
  void findById_hit_returnsProfile() {
    SmtpProfile p = profile("abc");
    p.setId("abc-id");
    when(repository.findById("abc-id")).thenReturn(Optional.of(p));

    Optional<SmtpProfile> result = service.findById("abc-id");

    assertThat(result).contains(p);
  }

  @Test
  @DisplayName("findById 不存在 → 返回 empty")
  void findById_miss_returnsEmpty() {
    when(repository.findById("nope")).thenReturn(Optional.empty());

    Optional<SmtpProfile> result = service.findById("nope");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findAll 返回 repository 全部")
  void findAll_returnsAll() {
    SmtpProfile p1 = profile("a");
    SmtpProfile p2 = profile("b");
    when(repository.findAll()).thenReturn(List.of(p1, p2));

    List<SmtpProfile> result = service.findAll();

    assertThat(result).containsExactly(p1, p2);
  }

  @Test
  @DisplayName("update 不存在的 id → 抛 IllegalArgumentException")
  void update_notFound_throws() {
    SmtpProfile input = profile("x");
    input.setId("ghost-id");
    when(repository.findById("ghost-id")).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.update("ghost-id", input));
  }

  @Test
  @DisplayName("delete 调 repository.deleteById")
  void delete_delegates() {
    service.delete("some-id");
    verify(repository).deleteById("some-id");
  }
}
```

- [ ] **Step 3.2：跑测试确认全 fail（service 未实现）**

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am test -Dtest='SmtpProfileServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

Expected: 编译失败（SmtpProfileService 未定义）— TDD red phase.

- [ ] **Step 3.3：实现 SmtpProfileService**

写入 `veriguard-api/src/main/java/io/veriguard/service/SmtpProfileService.java`：

```java
package io.veriguard.service;

import io.veriguard.database.model.SmtpProfile;
import io.veriguard.database.repository.SmtpProfileRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SmtpProfileService {

  private final SmtpProfileRepository repository;

  @Transactional
  public SmtpProfile create(SmtpProfile profile) {
    Optional<SmtpProfile> existing = repository.findByName(profile.getName());
    if (existing.isPresent()) {
      throw new IllegalArgumentException(
          "SMTP profile name already exists: " + profile.getName());
    }
    return repository.save(profile);
  }

  public Optional<SmtpProfile> findById(String id) {
    return repository.findById(id);
  }

  public List<SmtpProfile> findAll() {
    List<SmtpProfile> result = new java.util.ArrayList<>();
    repository.findAll().forEach(result::add);
    return result;
  }

  @Transactional
  public SmtpProfile update(String id, SmtpProfile updates) {
    SmtpProfile existing =
        repository
            .findById(id)
            .orElseThrow(
                () -> new IllegalArgumentException("SMTP profile not found: " + id));
    existing.setName(updates.getName());
    existing.setHost(updates.getHost());
    existing.setPort(updates.getPort());
    existing.setAuthType(updates.getAuthType());
    existing.setUsername(updates.getUsername());
    existing.setPassword(updates.getPassword());
    existing.setTlsMode(updates.getTlsMode());
    existing.setDefaultFrom(updates.getDefaultFrom());
    existing.setDefaultReplyTo(updates.getDefaultReplyTo());
    return repository.save(existing);
  }

  @Transactional
  public void delete(String id) {
    repository.deleteById(id);
  }
}
```

- [ ] **Step 3.4：跑测试确认全 pass**

```bash
mvn -pl veriguard-api -am test -Dtest='SmtpProfileServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | head -5
```

Expected: `Tests run: 7, Failures: 0, Errors: 0` + BUILD SUCCESS.

- [ ] **Step 3.5：spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/service/SmtpProfileService.java \
        veriguard-api/src/test/java/io/veriguard/service/SmtpProfileServiceTest.java
git commit -m "$(cat <<'EOF'
执行：SmtpProfileService + 7 单测（B-ii PR-B Step 3）

CRUD + name 唯一性校验：
- create：重名抛 IllegalArgumentException
- findById / findAll：直接 delegate repository
- update：实体不存在抛 IllegalArgumentException；存在则覆盖所有字段
- delete：delegate repository.deleteById

7 场景 TDD 覆盖 create 正常 / 重名 / findById 命中 / 不命中 / findAll /
update 不存在 / delete delegate.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：SmtpProfile REST API（CRUD）

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/rest/smtp_profile/SmtpProfileApi.java`
- Create: `veriguard-api/src/main/java/io/veriguard/rest/smtp_profile/form/SmtpProfileInput.java`

### Steps

- [ ] **Step 4.1：读现有 REST API 风格参考（如 AssetGroupApi）**

```bash
find veriguard-api/src/main/java/io/veriguard/rest -name '*Api.java' | head -5
grep -nE '@PostMapping|@PutMapping|@DeleteMapping|@RBAC' veriguard-api/src/main/java/io/veriguard/rest/asset_group/AssetGroupApi.java | head -10
```

了解 `@RBAC` 注解 + URI 常量 + DTO 输入模式。

- [ ] **Step 4.2：创建 SmtpProfileInput DTO**

```java
package io.veriguard.rest.smtp_profile.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.SmtpProfile;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SmtpProfileInput(
    @JsonProperty("smtp_profile_name") @NotBlank String name,
    @JsonProperty("smtp_profile_host") @NotBlank String host,
    @JsonProperty("smtp_profile_port") @NotNull @Min(1) Integer port,
    @JsonProperty("smtp_profile_auth_type") @NotNull SmtpProfile.AUTH_TYPE authType,
    @JsonProperty("smtp_profile_username") String username,
    @JsonProperty("smtp_profile_password") String password,
    @JsonProperty("smtp_profile_tls_mode") @NotNull SmtpProfile.TLS_MODE tlsMode,
    @JsonProperty("smtp_profile_default_from") @NotBlank String defaultFrom,
    @JsonProperty("smtp_profile_default_reply_to") String defaultReplyTo) {

  public SmtpProfile toEntity(SmtpProfile target) {
    target.setName(this.name);
    target.setHost(this.host);
    target.setPort(this.port);
    target.setAuthType(this.authType);
    target.setUsername(this.username);
    target.setPassword(this.password);
    target.setTlsMode(this.tlsMode);
    target.setDefaultFrom(this.defaultFrom);
    target.setDefaultReplyTo(this.defaultReplyTo);
    return target;
  }
}
```

- [ ] **Step 4.3：创建 SmtpProfileApi REST controller**

```java
package io.veriguard.rest.smtp_profile;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.model.SmtpProfile;
import io.veriguard.rest.smtp_profile.form.SmtpProfileInput;
import io.veriguard.service.SmtpProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SmtpProfileApi {

  public static final String SMTP_PROFILE_URI = "/api/smtp_profiles";

  private final SmtpProfileService service;

  @LogExecutionTime
  @GetMapping(SMTP_PROFILE_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SETTINGS)
  public List<SmtpProfile> findAll() {
    return service.findAll();
  }

  @LogExecutionTime
  @GetMapping(SMTP_PROFILE_URI + "/{id}")
  @RBAC(resourceId = "#id", actionPerformed = Action.READ, resourceType = ResourceType.SETTINGS)
  public SmtpProfile findById(@PathVariable @NotBlank final String id) {
    return service
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("SMTP profile not found: " + id));
  }

  @LogExecutionTime
  @PostMapping(SMTP_PROFILE_URI)
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SETTINGS)
  public SmtpProfile create(@Valid @RequestBody final SmtpProfileInput input) {
    SmtpProfile profile = new SmtpProfile();
    input.toEntity(profile);
    return service.create(profile);
  }

  @LogExecutionTime
  @PutMapping(SMTP_PROFILE_URI + "/{id}")
  @RBAC(resourceId = "#id", actionPerformed = Action.WRITE, resourceType = ResourceType.SETTINGS)
  public SmtpProfile update(
      @PathVariable @NotBlank final String id, @Valid @RequestBody final SmtpProfileInput input) {
    SmtpProfile patch = new SmtpProfile();
    input.toEntity(patch);
    return service.update(id, patch);
  }

  @LogExecutionTime
  @DeleteMapping(SMTP_PROFILE_URI + "/{id}")
  @RBAC(resourceId = "#id", actionPerformed = Action.DELETE, resourceType = ResourceType.SETTINGS)
  public void delete(@PathVariable @NotBlank final String id) {
    service.delete(id);
  }
}
```

注：若 `ResourceType.SETTINGS` 不存在，用 `ResourceType.SCENARIO`（与 AttackChainApi 同款 fallback），通过 grep `ResourceType` 现有值确认。

- [ ] **Step 4.4：编译 + 基线测试**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest,SmtpProfileServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: BUILD SUCCESS / 18 + 7 测试过.

- [ ] **Step 4.5：spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/rest/smtp_profile/SmtpProfileApi.java \
        veriguard-api/src/main/java/io/veriguard/rest/smtp_profile/form/SmtpProfileInput.java
git commit -m "$(cat <<'EOF'
执行：SmtpProfile REST API CRUD（B-ii PR-B Step 4）

- GET /api/smtp_profiles 列表 + /api/smtp_profiles/{id} 详情
- POST /api/smtp_profiles 创建（重名抛错由 service 拦截）
- PUT /api/smtp_profiles/{id} 修改
- DELETE /api/smtp_profiles/{id} 删除
- SmtpProfileInput record DTO：含 host / port / auth / tls / default_from 字段 + Bean Validation
- 全部端点带 @RBAC(SETTINGS) 权限注解

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5：EmailContent 数据模型

**Why:** Email inject 的 content JSON 反序列化目标类，与 ManualContent 同模式。

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/email/model/EmailContent.java`

### Steps

- [ ] **Step 5.1：读 ManualContent 作为模板**

```bash
cat veriguard-api/src/main/java/io/veriguard/injectors/manual/model/ManualContent.java
```

- [ ] **Step 5.2：创建 EmailContent.java**

```java
package io.veriguard.injectors.email.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.model.attack_chain_node.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailContent {

  @JsonProperty("smtp_profile_id")
  private String smtpProfileId;

  @JsonProperty("subject")
  private String subject;

  @JsonProperty("body_text")
  private String bodyText;

  @JsonProperty("body_html")
  private String bodyHtml;

  @JsonProperty("from_alias")
  private String fromAlias;

  @JsonProperty("attachments")
  private List<String> attachments = new ArrayList<>();

  @JsonProperty("inline_links")
  private List<String> inlineLinks = new ArrayList<>();

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
```

- [ ] **Step 5.3：编译**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -3
```

Expected: BUILD SUCCESS.

- [ ] **Step 5.4：Commit**

```bash
git add veriguard-api/src/main/java/io/veriguard/injectors/email/model/EmailContent.java
git commit -m "$(cat <<'EOF'
执行：EmailContent 数据模型（B-ii PR-B Step 5）

Email inject 的 content JSON 反序列化目标类（与 ManualContent 同模式）：
- smtp_profile_id：本次 inject 使用的 SMTP profile
- subject / body_text / body_html / from_alias：邮件构造
- attachments：附件 Document id 列表
- inline_links：正文内嵌钓鱼 URL 列表（运行时模板替换用）
- expectations：节点期望评估

后续 EmailExecutor.process 调 contentConvert(injection, EmailContent.class) 反序列化.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6：MailInjector 服务 + 6 单测（TDD）

**Why:** 用 jakarta.mail 直接构造 Session/Transport（不复用 SmtpService，后者绑定单一全局 SMTP 配置用于平台通知），按 SmtpProfile 动态构造发送上下文。

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/email/service/MailInjector.java`
- Create: `veriguard-api/src/test/java/io/veriguard/injectors/email/service/MailInjectorTest.java`

### Steps

- [ ] **Step 6.1：先写失败测试**

```java
package io.veriguard.injectors.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.database.model.SmtpProfile;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailInjectorTest {

  private SmtpProfile profile() {
    SmtpProfile p = new SmtpProfile();
    p.setName("test");
    p.setHost("smtp.example.com");
    p.setPort(587);
    p.setAuthType(SmtpProfile.AUTH_TYPE.PASSWORD);
    p.setUsername("u");
    p.setPassword("p");
    p.setTlsMode(SmtpProfile.TLS_MODE.STARTTLS);
    p.setDefaultFrom("noreply@example.com");
    return p;
  }

  @Test
  @DisplayName("buildMimeMessage 设置主题 / from / to / 正文")
  void buildMimeMessage_setsAllFields() throws Exception {
    MailInjector injector = new MailInjector();
    MimeMessage msg =
        injector.buildMimeMessage(
            profile(),
            null,
            "Subject Test",
            "Body text",
            null,
            List.of("victim@example.com"));

    assertThat(msg.getSubject()).isEqualTo("Subject Test");
    assertThat(msg.getContent()).isEqualTo("Body text");
    Address[] from = msg.getFrom();
    assertThat(from).hasSize(1);
    assertThat(((InternetAddress) from[0]).getAddress()).isEqualTo("noreply@example.com");
    Address[] to = msg.getRecipients(Message.RecipientType.TO);
    assertThat(to).hasSize(1);
    assertThat(((InternetAddress) to[0]).getAddress()).isEqualTo("victim@example.com");
  }

  @Test
  @DisplayName("from_alias 覆盖 default_from")
  void fromAlias_overridesDefault() throws Exception {
    MailInjector injector = new MailInjector();
    MimeMessage msg =
        injector.buildMimeMessage(
            profile(),
            "IT Support <support@phish.com>",
            "Subject",
            "Body",
            null,
            List.of("victim@example.com"));

    Address[] from = msg.getFrom();
    assertThat(((InternetAddress) from[0]).getAddress()).isEqualTo("support@phish.com");
    assertThat(((InternetAddress) from[0]).getPersonal()).isEqualTo("IT Support");
  }

  @Test
  @DisplayName("html body → content type text/html")
  void htmlBody_setsHtmlContentType() throws Exception {
    MailInjector injector = new MailInjector();
    MimeMessage msg =
        injector.buildMimeMessage(
            profile(), null, "S", null, "<b>Hi</b>", List.of("v@example.com"));

    assertThat(msg.getContentType()).startsWith("text/html");
    assertThat(msg.getContent()).isEqualTo("<b>Hi</b>");
  }

  @Test
  @DisplayName("空收件人 → 抛 IllegalArgumentException")
  void emptyRecipients_throws() {
    MailInjector injector = new MailInjector();

    assertThatThrownBy(
            () -> injector.buildMimeMessage(profile(), null, "S", "B", null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recipient");
  }

  @Test
  @DisplayName("空 subject → 抛 IllegalArgumentException")
  void blankSubject_throws() {
    MailInjector injector = new MailInjector();

    assertThatThrownBy(
            () ->
                injector.buildMimeMessage(
                    profile(), null, " ", "B", null, List.of("v@example.com")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("subject");
  }

  @Test
  @DisplayName("body_text 与 body_html 都为空 → 抛 IllegalArgumentException")
  void bothBodiesEmpty_throws() {
    MailInjector injector = new MailInjector();

    assertThatThrownBy(
            () ->
                injector.buildMimeMessage(
                    profile(), null, "S", null, null, List.of("v@example.com")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("body");
  }
}
```

- [ ] **Step 6.2：跑测试确认全 fail**

```bash
mvn -pl veriguard-api -am test -Dtest='MailInjectorTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

Expected: 编译失败（MailInjector 未定义）.

- [ ] **Step 6.3：实现 MailInjector**

```java
package io.veriguard.injectors.email.service;

import io.veriguard.database.model.SmtpProfile;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mail inject 专用发送服务（B-ii PR-B）.
 *
 * <p>独立于 {@link SmtpService}（后者绑定单一全局 SMTP 用于平台通知），按 SmtpProfile
 * 动态构造 jakarta.mail Session 与 Transport，支持每个 inject 选用不同 profile.
 */
@Service
@Slf4j
public class MailInjector {

  /**
   * 构造 MimeMessage 但不发送（便于单测验证消息结构 + 拆分发送步骤）.
   */
  public MimeMessage buildMimeMessage(
      SmtpProfile profile,
      String fromAlias,
      String subject,
      String bodyText,
      String bodyHtml,
      List<String> recipients)
      throws Exception {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Mail subject is required");
    }
    boolean hasText = bodyText != null && !bodyText.isBlank();
    boolean hasHtml = bodyHtml != null && !bodyHtml.isBlank();
    if (!hasText && !hasHtml) {
      throw new IllegalArgumentException("Mail body (text or html) is required");
    }
    if (recipients == null || recipients.isEmpty()) {
      throw new IllegalArgumentException("Mail recipient list cannot be empty");
    }

    Session session = createSession(profile);
    MimeMessage msg = new MimeMessage(session);

    InternetAddress from =
        fromAlias != null && !fromAlias.isBlank()
            ? new InternetAddress(fromAlias)
            : new InternetAddress(profile.getDefaultFrom());
    msg.setFrom(from);

    if (profile.getDefaultReplyTo() != null && !profile.getDefaultReplyTo().isBlank()) {
      msg.setReplyTo(new InternetAddress[] {new InternetAddress(profile.getDefaultReplyTo())});
    }

    InternetAddress[] toAddrs = new InternetAddress[recipients.size()];
    for (int i = 0; i < recipients.size(); i++) {
      toAddrs[i] = new InternetAddress(recipients.get(i));
    }
    msg.setRecipients(Message.RecipientType.TO, toAddrs);

    msg.setSubject(subject, "utf-8");

    if (hasHtml) {
      msg.setContent(bodyHtml, "text/html; charset=utf-8");
    } else {
      msg.setText(bodyText, "utf-8");
    }
    return msg;
  }

  /** 发送已构造好的 MimeMessage（独立步骤，便于失败处理 / 重试）. */
  public void send(SmtpProfile profile, MimeMessage message) throws Exception {
    Session session = message.getSession();
    try (Transport transport = session.getTransport()) {
      if (profile.getAuthType() == SmtpProfile.AUTH_TYPE.PASSWORD) {
        transport.connect(
            profile.getHost(), profile.getPort(), profile.getUsername(), profile.getPassword());
      } else {
        transport.connect(profile.getHost(), profile.getPort(), null, null);
      }
      transport.sendMessage(message, message.getAllRecipients());
    }
  }

  private Session createSession(SmtpProfile profile) {
    Properties props = new Properties();
    props.put("mail.smtp.host", profile.getHost());
    props.put("mail.smtp.port", String.valueOf(profile.getPort()));
    props.put("mail.smtp.auth", profile.getAuthType() == SmtpProfile.AUTH_TYPE.PASSWORD);
    switch (profile.getTlsMode()) {
      case STARTTLS -> props.put("mail.smtp.starttls.enable", true);
      case TLS -> props.put("mail.smtp.ssl.enable", true);
      case NONE -> {
        /* no-op */
      }
    }
    return Session.getInstance(props);
  }
}
```

- [ ] **Step 6.4：跑测试确认全 pass**

```bash
mvn -pl veriguard-api -am test -Dtest='MailInjectorTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | head -5
```

Expected: `Tests run: 6, Failures: 0, Errors: 0` + BUILD SUCCESS.

- [ ] **Step 6.5：spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/injectors/email/service/MailInjector.java \
        veriguard-api/src/test/java/io/veriguard/injectors/email/service/MailInjectorTest.java
git commit -m "$(cat <<'EOF'
执行：MailInjector 服务 + 6 单测（B-ii PR-B Step 6）

按 SmtpProfile 构造 jakarta.mail Session / Transport，独立于现有 SmtpService
（后者用于平台通知邮件、绑定单一全局 SMTP）.

- buildMimeMessage：构造 MimeMessage（不发送），验证主题/正文必填、收件人非空
- send：通过 Transport.sendMessage 实际发送，按 auth_type 选择是否认证

6 场景 TDD 覆盖：完整字段 / from_alias 覆盖 / HTML 正文 / 空收件人 /
空主题 / 空 body（两都空）.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7：EmailContract Contractor 实现

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/email/EmailContract.java`

### Steps

- [ ] **Step 7.1：参考 ManualContract 模板创建 EmailContract**

```java
package io.veriguard.injectors.email;

import static io.veriguard.helper.SupportedLanguage.en;
import static io.veriguard.helper.SupportedLanguage.fr;
import static io.veriguard.injector_contract.Contract.executableContract;
import static io.veriguard.injector_contract.ContractCardinality.Multiple;
import static io.veriguard.injector_contract.ContractDef.contractBuilder;
import static io.veriguard.injector_contract.fields.ContractAttachment.attachmentField;
import static io.veriguard.injector_contract.fields.ContractExpectations.expectationsField;
import static io.veriguard.injector_contract.fields.ContractTeam.teamField;
import static io.veriguard.injector_contract.fields.ContractText.textField;
import static io.veriguard.injector_contract.fields.ContractTextArea.textAreaField;

import io.veriguard.database.model.Endpoint;
import io.veriguard.helper.SupportedLanguage;
import io.veriguard.injector_contract.Contract;
import io.veriguard.injector_contract.ContractConfig;
import io.veriguard.injector_contract.Contractor;
import io.veriguard.injector_contract.ContractorIcon;
import io.veriguard.injector_contract.fields.ContractElement;
import io.veriguard.rest.domain.enums.PresetDomain;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EmailContract extends Contractor {

  public static final String TYPE = "veriguard_mail";

  public static final String EMAIL_DEFAULT = "0b78f5a1-2d49-4f5e-a7e1-b3c7f5d9e1a4";

  private final List<Contract> contracts;
  private final ContractConfig config;

  public EmailContract() {
    ContractElement teams = teamField(Multiple);
    ContractElement subject = textField("subject", "Subject");
    ContractElement bodyText = textAreaField("body_text", "Body (plain text)");
    ContractElement bodyHtml = textAreaField("body_html", "Body (HTML)");
    ContractElement fromAlias = textField("from_alias", "From alias (override default sender)");
    ContractElement smtpProfileId = textField("smtp_profile_id", "SMTP profile id");
    ContractElement attachments = attachmentField(Multiple);
    ContractElement expectations = expectationsField();

    Map<SupportedLanguage, String> label = Map.of(en, "Email", fr, "E-mail");
    config = new ContractConfig(TYPE, label, "#1976d2", "#1976d2", "/img/icon-email.png");

    List<ContractElement> fields =
        contractBuilder()
            .mandatory(teams, subject, smtpProfileId)
            .optional(bodyText, bodyHtml, fromAlias, attachments, expectations)
            .build();

    contracts =
        List.of(
            executableContract(
                config,
                EMAIL_DEFAULT,
                Map.of(en, "Send phishing / notification email", fr, "Envoyer un e-mail"),
                fields,
                List.of(Endpoint.PLATFORM_TYPE.Generic),
                false,
                Set.of(PresetDomain.EMAIL_INFILTRATION)));
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return config;
  }

  @Override
  public List<Contract> contracts() {
    return contracts;
  }

  @Override
  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-email.png");
    return new ContractorIcon(iconStream);
  }
}
```

注：如 `ContractDef.contractBuilder()` 的 `.mandatory(...)` 方法签名跟此处使用不一致，参考 ManualContract 中 `.mandatoryOnCondition(teams, expectations)` 调用方式调整。先 read `/Users/lamba/github/Veriguard/veriguard-framework/src/main/java/io/veriguard/injector_contract/ContractDef.java` 确认实际签名。

- [ ] **Step 7.2：放置默认图标**

```bash
ls veriguard-api/src/main/resources/img/icon-*.png | head -5
```

如果 `icon-email.png` 不存在，复制 `icon-manual.png` 临时使用：

```bash
cp veriguard-api/src/main/resources/img/icon-manual.png \
   veriguard-api/src/main/resources/img/icon-email.png 2>/dev/null || \
   touch veriguard-api/src/main/resources/img/icon-email.png
```

正式 icon 在 follow-up 中补充设计师产物。

- [ ] **Step 7.3：编译验证**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 7.4：Commit**

```bash
git add veriguard-api/src/main/java/io/veriguard/injectors/email/EmailContract.java \
        veriguard-api/src/main/resources/img/icon-email.png 2>/dev/null
git commit -m "$(cat <<'EOF'
执行：EmailContract Contractor 注册（B-ii PR-B Step 7）

注册 veriguard_mail inject 类型元数据：
- TYPE = "veriguard_mail"，默认 contract id 0b78f5a1...
- 字段定义：teams（收件人团队）/ subject / body_text / body_html /
  from_alias / smtp_profile_id / attachments / expectations
- ContractConfig：颜色 #1976d2（MUI 蓝）+ 邮件图标
- executableContract（非 manual，可自动化执行）+ EMAIL_INFILTRATION domain

后续 EmailExecutor + EmailNodeExecutorIntegration 用此 contract 与 inject 关联.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8：EmailExecutor 实现

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/email/EmailExecutor.java`

### Steps

- [ ] **Step 8.1：读 ManualExecutor 模板**

```bash
cat veriguard-api/src/main/java/io/veriguard/injectors/manual/ManualExecutor.java
```

- [ ] **Step 8.2：创建 EmailExecutor**

```java
package io.veriguard.injectors.email;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.database.model.ExecutionTrace.getNewSuccessTrace;

import io.veriguard.database.model.Execution;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.database.model.SmtpProfile;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutor;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.email.model.EmailContent;
import io.veriguard.injectors.email.service.MailInjector;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.model.Expectation;
import io.veriguard.model.expectation.ManualExpectation;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.SmtpProfileService;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmailExecutor extends NodeExecutor {

  private final SmtpProfileService smtpProfileService;
  private final MailInjector mailInjector;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  public EmailExecutor(
      NodeExecutorContext context,
      SmtpProfileService smtpProfileService,
      MailInjector mailInjector,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(context);
    this.smtpProfileService = smtpProfileService;
    this.mailInjector = mailInjector;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableNode injection)
      throws Exception {
    EmailContent content = contentConvert(injection, EmailContent.class);

    String profileId = content.getSmtpProfileId();
    if (profileId == null || profileId.isBlank()) {
      execution.addTrace(
          getNewErrorTrace("smtp_profile_id is required", ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }
    Optional<SmtpProfile> profileOpt = smtpProfileService.findById(profileId);
    if (profileOpt.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "SMTP profile not found: " + profileId, ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }
    SmtpProfile profile = profileOpt.get();

    List<String> recipients = extractTeamEmails(injection);
    if (recipients.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "No team player has a valid email address", ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }

    int sent = 0;
    List<String> failures = new ArrayList<>();
    for (String recipient : recipients) {
      try {
        MimeMessage msg =
            mailInjector.buildMimeMessage(
                profile,
                content.getFromAlias(),
                content.getSubject(),
                content.getBodyText(),
                content.getBodyHtml(),
                List.of(recipient));
        mailInjector.send(profile, msg);
        sent++;
      } catch (Exception e) {
        log.warn("Failed to send mail to {}: {}", recipient, e.getMessage());
        failures.add(recipient + " (" + e.getMessage() + ")");
      }
    }

    List<Expectation> expectations =
        content.getExpectations().stream()
            .flatMap(
                entry ->
                    switch (entry.getType()) {
                      case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                      default -> Stream.of();
                    })
            .toList();

    attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(
        injection, expectations);

    if (failures.isEmpty()) {
      execution.addTrace(
          getNewSuccessTrace(
              "Sent email to " + sent + " recipient(s)", ExecutionTraceAction.COMPLETE));
    } else {
      execution.addTrace(
          getNewErrorTrace(
              "Sent " + sent + " mail(s), " + failures.size() + " failed: " + failures,
              ExecutionTraceAction.COMPLETE));
    }
    return new ExecutionProcess(false);
  }

  private List<String> extractTeamEmails(ExecutableNode injection) {
    List<String> emails = new ArrayList<>();
    injection
        .getTeams()
        .forEach(
            team ->
                team.getUsers()
                    .forEach(
                        user -> {
                          String email = user.getEmail();
                          if (email != null && !email.isBlank()) {
                            emails.add(email);
                          }
                        }));
    return emails;
  }
}
```

注：`injection.getTeams()` / `team.getUsers()` / `user.getEmail()` 的实际方法名以 `ExecutableNode`、`Team`、`User` 实体为准。如果方法名不同，按实际命名调整：

```bash
grep -nE 'public.*getTeams\|public.*getUsers\|public.*getPlayers' veriguard-model/src/main/java/io/veriguard/database/model/Team.java veriguard-execution/src/main/java/io/veriguard/execution/ExecutableNode.java 2>/dev/null | head -10
```

- [ ] **Step 8.3：编译 + 基线测试**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest,SmtpProfileServiceTest,MailInjectorTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: BUILD SUCCESS + 18 + 7 + 6 测试过.

- [ ] **Step 8.4：spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/injectors/email/EmailExecutor.java
git commit -m "$(cat <<'EOF'
执行：EmailExecutor 实现（B-ii PR-B Step 8）

NodeExecutor 子类，process(Execution, ExecutableNode) 实现：
- 反序列化 content 为 EmailContent
- 查 SMTP profile（未找到则 error trace）
- 抽取 team players 邮箱清单（无有效邮箱则 error）
- 逐个收件人构造 MimeMessage + send（失败计入 failures 但继续后续）
- 节点期望（ManualExpectation）保存
- 全部成功 → success trace；有失败 → error trace 含失败明细

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9：EmailNodeExecutorIntegration + IntegrationFactory

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/email/EmailNodeExecutorIntegration.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/email/EmailNodeExecutorIntegrationFactory.java`

### Steps

- [ ] **Step 9.1：读 Manual 同类文件作为模板**

```bash
cat veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/manual/ManualNodeExecutorIntegration.java
cat veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/manual/ManualNodeExecutorIntegrationFactory.java
```

- [ ] **Step 9.2：创建 EmailNodeExecutorIntegration**

```java
package io.veriguard.integration.impl.injectors.email;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.email.EmailContract;
import io.veriguard.injectors.email.EmailExecutor;
import io.veriguard.injectors.email.service.MailInjector;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.IntegrationInMemory;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.SmtpProfileService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class EmailNodeExecutorIntegration extends IntegrationInMemory {
  private static final String EMAIL_INJECTOR_NAME = "Email";
  public static final String EMAIL_INJECTOR_ID = "21f4a8d2-7b3e-4c5a-b7f9-d8e1a4b2c3f5";

  private final EmailContract emailContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final NodeExecutorService nodeExecutorService;
  private final SmtpProfileService smtpProfileService;
  private final MailInjector mailInjector;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  @QualifiedComponent(identifier = {EmailContract.TYPE, EMAIL_INJECTOR_ID})
  private EmailExecutor emailExecutor;

  public EmailNodeExecutorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EmailContract emailContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      SmtpProfileService smtpProfileService,
      MailInjector mailInjector,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.emailContract = emailContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.smtpProfileService = smtpProfileService;
    this.mailInjector = mailInjector;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    nodeExecutorService.registerBuiltinNodeExecutor(
        EMAIL_INJECTOR_ID,
        EMAIL_INJECTOR_NAME,
        emailContract,
        true,
        "generic",
        null,
        null,
        false,
        List.of());
    this.emailExecutor =
        new EmailExecutor(
            nodeExecutorContext,
            smtpProfileService,
            mailInjector,
            attackChainNodeExpectationService);
  }

  @Override
  protected void innerStop() {
    // not stoppable
  }
}
```

- [ ] **Step 9.3：创建 EmailNodeExecutorIntegrationFactory**

```java
package io.veriguard.integration.impl.injectors.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.email.EmailContract;
import io.veriguard.injectors.email.service.MailInjector;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.SmtpProfileService;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EmailNodeExecutorIntegrationFactory extends IntegrationFactory {

  private final EmailContract emailContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final ConnectorInstanceService connectorInstanceService;
  private final NodeExecutorService nodeExecutorService;
  private final SmtpProfileService smtpProfileService;
  private final MailInjector mailInjector;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final ComponentRequestEngine componentRequestEngine;

  public EmailNodeExecutorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      EmailContract emailContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      SmtpProfileService smtpProfileService,
      MailInjector mailInjector,
      AttackChainNodeExpectationService attackChainNodeExpectationService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.emailContract = emailContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.smtpProfileService = smtpProfileService;
    this.mailInjector = mailInjector;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances() {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            EmailNodeExecutorIntegration.EMAIL_INJECTOR_ID,
            this.getClassName(),
            ConnectorType.INJECTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new EmailNodeExecutorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        emailContract,
        nodeExecutorContext,
        nodeExecutorService,
        smtpProfileService,
        mailInjector,
        attackChainNodeExpectationService);
  }
}
```

- [ ] **Step 9.4：编译 + 基线测试**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest,SmtpProfileServiceTest,MailInjectorTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: BUILD SUCCESS + 18 + 7 + 6 测试过.

- [ ] **Step 9.5：spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/email/EmailNodeExecutorIntegration.java \
        veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/email/EmailNodeExecutorIntegrationFactory.java
git commit -m "$(cat <<'EOF'
执行：EmailNodeExecutorIntegration + Factory（B-ii PR-B Step 9）

参考 ManualNodeExecutorIntegration 模式注册 veriguard_mail inject 类型到
NodeExecutorService：
- EMAIL_INJECTOR_ID 固定 UUID（21f4a8d2-...），用于 ConnectorInstance autostart
- innerStart 调 nodeExecutorService.registerBuiltinNodeExecutor 注册 contract
- @QualifiedComponent 标记 EmailExecutor 实例
- Factory @Service 提供 spawn + findRelatedInstances 给 autostart 机制使用

启动后 veriguard_mail inject 类型对前端可见，用户可选用例并配 SMTP profile.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10：前端 SMTP Profile 管理界面

**Files:**

- Modify: `veriguard-front/src/utils/api-types.d.ts`（手工加 SmtpProfile / SmtpProfileInput 类型）
- Create: `veriguard-front/src/actions/smtp_profiles/smtp_profile-action.ts`
- Create: `veriguard-front/src/admin/components/integrations/smtp_profiles/SmtpProfiles.tsx`
- Create: `veriguard-front/src/admin/components/integrations/smtp_profiles/SmtpProfileForm.tsx`
- Modify: `veriguard-front/src/admin/Index.tsx`（注册路由）
- Modify: `veriguard-front/src/utils/lang/zh.json` + `en.json`（i18n keys）

### Steps

- [ ] **Step 10.1：api-types.d.ts 手工加类型**

打开 `veriguard-front/src/utils/api-types.d.ts`，在文件末尾或合适位置加：

```ts
export interface SmtpProfile {
  smtp_profile_id: string;
  smtp_profile_name: string;
  smtp_profile_host: string;
  smtp_profile_port: number;
  smtp_profile_auth_type: 'none' | 'password';
  smtp_profile_username?: string;
  smtp_profile_password?: string;
  smtp_profile_tls_mode: 'none' | 'starttls' | 'tls';
  smtp_profile_default_from: string;
  smtp_profile_default_reply_to?: string;
  smtp_profile_created_at?: string;
  smtp_profile_updated_at?: string;
}

export interface SmtpProfileInput {
  smtp_profile_name: string;
  smtp_profile_host: string;
  smtp_profile_port: number;
  smtp_profile_auth_type: 'none' | 'password';
  smtp_profile_username?: string;
  smtp_profile_password?: string;
  smtp_profile_tls_mode: 'none' | 'starttls' | 'tls';
  smtp_profile_default_from: string;
  smtp_profile_default_reply_to?: string;
}
```

- [ ] **Step 10.2：创建 smtp_profile-action.ts**

```ts
import { type Dispatch } from 'redux';

import {
  delReferential,
  getReferential,
  postReferential,
  putReferential,
} from '../../utils/Action';
import { type SmtpProfile, type SmtpProfileInput } from '../../utils/api-types';
import * as schema from '../Schema';

const SMTP_PROFILE_URI = '/api/smtp_profiles';

const smtpProfileSchema = new schema.Entity('smtp_profiles', { idAttribute: 'smtp_profile_id' });
const smtpProfileSchemaArray = [smtpProfileSchema];

export const fetchSmtpProfiles = () => (dispatch: Dispatch) =>
  getReferential(smtpProfileSchemaArray, SMTP_PROFILE_URI)(dispatch);

export const createSmtpProfile = (input: SmtpProfileInput) => (dispatch: Dispatch) =>
  postReferential<SmtpProfile>(smtpProfileSchema, SMTP_PROFILE_URI, input)(dispatch);

export const updateSmtpProfile =
  (id: string, input: SmtpProfileInput) =>
  (dispatch: Dispatch) =>
    putReferential(smtpProfileSchema, `${SMTP_PROFILE_URI}/${id}`, input)(dispatch);

export const deleteSmtpProfile = (id: string) => (dispatch: Dispatch) =>
  delReferential(`${SMTP_PROFILE_URI}/${id}`, 'smtp_profiles', id)(dispatch);
```

注：如 `delReferential` / `postReferential` 等函数签名不同，参考其他 action 文件（如 `attack_chains/attack_chain-actions.ts`）调整。

- [ ] **Step 10.3：创建 SmtpProfileForm.tsx**

```tsx
/* eslint-disable i18next/no-literal-string -- Phase B-ii PR-B 二开 UI 硬编码中文，未来统一 i18n 清洗。 */
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { type SmtpProfile, type SmtpProfileInput } from '../../../../utils/api-types';

interface Props {
  open: boolean;
  initial?: SmtpProfile;
  onClose: () => void;
  onSubmit: (input: SmtpProfileInput) => Promise<void>;
}

const defaultInput: SmtpProfileInput = {
  smtp_profile_name: '',
  smtp_profile_host: '',
  smtp_profile_port: 587,
  smtp_profile_auth_type: 'password',
  smtp_profile_username: '',
  smtp_profile_password: '',
  smtp_profile_tls_mode: 'starttls',
  smtp_profile_default_from: '',
  smtp_profile_default_reply_to: '',
};

const SmtpProfileForm: FunctionComponent<Props> = ({ open, initial, onClose, onSubmit }) => {
  const [form, setForm] = useState<SmtpProfileInput>(defaultInput);

  useEffect(() => {
    if (open) {
      if (initial) {
        setForm({
          smtp_profile_name: initial.smtp_profile_name,
          smtp_profile_host: initial.smtp_profile_host,
          smtp_profile_port: initial.smtp_profile_port,
          smtp_profile_auth_type: initial.smtp_profile_auth_type,
          smtp_profile_username: initial.smtp_profile_username ?? '',
          smtp_profile_password: initial.smtp_profile_password ?? '',
          smtp_profile_tls_mode: initial.smtp_profile_tls_mode,
          smtp_profile_default_from: initial.smtp_profile_default_from,
          smtp_profile_default_reply_to: initial.smtp_profile_default_reply_to ?? '',
        });
      } else {
        setForm(defaultInput);
      }
    }
  }, [open, initial]);

  const handleSubmit = async () => {
    await onSubmit(form);
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{initial ? '编辑 SMTP 配置' : '新建 SMTP 配置'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ marginTop: 1 }}>
          <TextField
            label="名称"
            value={form.smtp_profile_name}
            onChange={e => setForm({ ...form, smtp_profile_name: e.target.value })}
            fullWidth
            required
          />
          <TextField
            label="SMTP 主机"
            value={form.smtp_profile_host}
            onChange={e => setForm({ ...form, smtp_profile_host: e.target.value })}
            fullWidth
            required
          />
          <TextField
            label="端口"
            type="number"
            value={form.smtp_profile_port}
            onChange={e => setForm({ ...form, smtp_profile_port: Number(e.target.value) })}
            fullWidth
            required
          />
          <FormControl fullWidth>
            <InputLabel>认证类型</InputLabel>
            <Select
              label="认证类型"
              value={form.smtp_profile_auth_type}
              onChange={e =>
                setForm({
                  ...form,
                  smtp_profile_auth_type: e.target.value as 'none' | 'password',
                })
              }
            >
              <MenuItem value="none">无</MenuItem>
              <MenuItem value="password">用户名密码</MenuItem>
            </Select>
          </FormControl>
          {form.smtp_profile_auth_type === 'password' && (
            <Box>
              <TextField
                label="用户名"
                value={form.smtp_profile_username}
                onChange={e => setForm({ ...form, smtp_profile_username: e.target.value })}
                fullWidth
                margin="dense"
              />
              <TextField
                label="密码"
                type="password"
                value={form.smtp_profile_password}
                onChange={e => setForm({ ...form, smtp_profile_password: e.target.value })}
                fullWidth
                margin="dense"
              />
            </Box>
          )}
          <FormControl fullWidth>
            <InputLabel>TLS 模式</InputLabel>
            <Select
              label="TLS 模式"
              value={form.smtp_profile_tls_mode}
              onChange={e =>
                setForm({
                  ...form,
                  smtp_profile_tls_mode: e.target.value as 'none' | 'starttls' | 'tls',
                })
              }
            >
              <MenuItem value="none">无</MenuItem>
              <MenuItem value="starttls">STARTTLS</MenuItem>
              <MenuItem value="tls">TLS</MenuItem>
            </Select>
          </FormControl>
          <TextField
            label="默认发件人"
            value={form.smtp_profile_default_from}
            onChange={e => setForm({ ...form, smtp_profile_default_from: e.target.value })}
            fullWidth
            required
            helperText="例如 noreply@example.com"
          />
          <TextField
            label="默认回复地址"
            value={form.smtp_profile_default_reply_to}
            onChange={e => setForm({ ...form, smtp_profile_default_reply_to: e.target.value })}
            fullWidth
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>取消</Button>
        <Button variant="contained" onClick={handleSubmit}>
          保存
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default SmtpProfileForm;
```

- [ ] **Step 10.4：创建 SmtpProfiles.tsx 列表页**

```tsx
/* eslint-disable i18next/no-literal-string -- Phase B-ii PR-B 二开 UI */
import { Add, Delete, Edit } from '@mui/icons-material';
import {
  Button,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import {
  createSmtpProfile,
  deleteSmtpProfile,
  fetchSmtpProfiles,
  updateSmtpProfile,
} from '../../../../actions/smtp_profiles/smtp_profile-action';
import { type SmtpProfile, type SmtpProfileInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import SmtpProfileForm from './SmtpProfileForm';

const SmtpProfiles: FunctionComponent = () => {
  const dispatch = useAppDispatch();
  const [profiles, setProfiles] = useState<SmtpProfile[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<SmtpProfile | undefined>(undefined);

  const refresh = async () => {
    const result = (await dispatch(fetchSmtpProfiles() as unknown as () => Promise<unknown>))
      ?? { entities: { smtp_profiles: {} } };
    const map = (result as { entities?: { smtp_profiles?: Record<string, SmtpProfile> } }).entities
      ?.smtp_profiles ?? {};
    setProfiles(Object.values(map));
  };

  useEffect(() => {
    refresh();
  }, []);

  const handleCreate = async (input: SmtpProfileInput) => {
    await dispatch(createSmtpProfile(input) as unknown as () => Promise<unknown>);
    await refresh();
  };

  const handleUpdate = async (input: SmtpProfileInput) => {
    if (!editTarget) return;
    await dispatch(
      updateSmtpProfile(editTarget.smtp_profile_id, input) as unknown as () => Promise<unknown>,
    );
    await refresh();
  };

  const handleDelete = async (id: string) => {
    await dispatch(deleteSmtpProfile(id) as unknown as () => Promise<unknown>);
    await refresh();
  };

  return (
    <Paper sx={{ padding: 2 }}>
      <Stack direction="row" justifyContent="space-between" sx={{ marginBottom: 2 }}>
        <h2>SMTP 配置管理</h2>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => {
            setEditTarget(undefined);
            setFormOpen(true);
          }}
        >
          新建
        </Button>
      </Stack>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>名称</TableCell>
            <TableCell>主机 : 端口</TableCell>
            <TableCell>TLS</TableCell>
            <TableCell>默认发件人</TableCell>
            <TableCell>操作</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {profiles.map(p => (
            <TableRow key={p.smtp_profile_id}>
              <TableCell>{p.smtp_profile_name}</TableCell>
              <TableCell>{p.smtp_profile_host}:{p.smtp_profile_port}</TableCell>
              <TableCell>{p.smtp_profile_tls_mode}</TableCell>
              <TableCell>{p.smtp_profile_default_from}</TableCell>
              <TableCell>
                <IconButton
                  size="small"
                  onClick={() => {
                    setEditTarget(p);
                    setFormOpen(true);
                  }}
                >
                  <Edit fontSize="small" />
                </IconButton>
                <IconButton
                  size="small"
                  color="error"
                  onClick={() => handleDelete(p.smtp_profile_id)}
                >
                  <Delete fontSize="small" />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <SmtpProfileForm
        open={formOpen}
        initial={editTarget}
        onClose={() => setFormOpen(false)}
        onSubmit={editTarget ? handleUpdate : handleCreate}
      />
    </Paper>
  );
};

export default SmtpProfiles;
```

- [ ] **Step 10.5：注册路由（admin/Index.tsx）**

```bash
grep -nE 'Route.*executors\|Route.*integrations' veriguard-front/src/admin/Index.tsx | head -5
```

在合适位置（如其他 integrations 路由附近）加：

```tsx
import SmtpProfiles from './components/integrations/smtp_profiles/SmtpProfiles';

// 在 Routes 中加
<Route path="smtp_profiles" element={<SmtpProfiles />} />
```

并在导航菜单（如 LeftBar.tsx）加 SMTP 配置入口（参考其他 integrations 菜单项）。

- [ ] **Step 10.6：check-ts + lint + 测试**

```bash
cd veriguard-front
yarn check-ts 2>&1 | tail -5
yarn test 2>&1 | grep -E 'Test Files|Tests' | tail -3
```

Expected: 0 ts errors / 0 测试回归.

- [ ] **Step 10.7：Commit**

```bash
git add veriguard-front/src/utils/api-types.d.ts \
        veriguard-front/src/actions/smtp_profiles/smtp_profile-action.ts \
        veriguard-front/src/admin/components/integrations/smtp_profiles/SmtpProfiles.tsx \
        veriguard-front/src/admin/components/integrations/smtp_profiles/SmtpProfileForm.tsx \
        veriguard-front/src/admin/Index.tsx 2>/dev/null
# 如修改了 LeftBar 也加
git status -s
git commit -m "$(cat <<'EOF'
执行：前端 SMTP Profile 管理界面（B-ii PR-B Step 10）

- api-types.d.ts 手工加 SmtpProfile / SmtpProfileInput 类型
- smtp_profile-action.ts：fetchSmtpProfiles / createSmtpProfile /
  updateSmtpProfile / deleteSmtpProfile actions
- SmtpProfiles.tsx：列表 + 新建/编辑/删除按钮 + MUI Table
- SmtpProfileForm.tsx：Dialog 表单，含 host/port/auth/tls/default_from 字段
- admin/Index.tsx：注册 /smtp_profiles 路由

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11：前端 Email Inject 节点画布渲染

**Why:** 攻击编排画布上邮件 inject 节点显示邮件图标 + 蓝色 border。后端 Contract 已配置颜色 `#1976d2` + icon path，前端通常通过 `injector_contract_color` / `injector_contract_icon` 从 contract 元数据读取，本步只需验证现有通用渲染能识别新 inject 类型，可能需要补充节点图标 mapping。

**Files:**

- Modify: `veriguard-front/src/components/nodes/NodeAttackChainNode.tsx`（节点视觉，若需根据 contract type 切图标）

### Steps

- [ ] **Step 11.1：检查现有节点渲染如何取 inject 类型图标**

```bash
grep -nE 'injector_contract_color\|injector_contract_icon\|getNodeIcon\|injectIcon' veriguard-front/src/components/nodes/NodeAttackChainNode.tsx veriguard-front/src/components/nodes/NodeAttackChainNodeWrapper.tsx 2>/dev/null | head
```

- [ ] **Step 11.2：若画布已通用读取 contract 元数据，本步无需改代码**

通常 OpenBAS 框架的画布会用 contract 注册的 `config.color` + `config.icon` 自动渲染。EmailContract 已声明 `#1976d2` + `/img/icon-email.png`。Step 11.1 grep 结果决定本步是否需要进一步改动。

如果需要补充 mapping（例如前端有写死的 `executorType → icon` map），找到该 map 加 `veriguard_mail: '/img/icon-email.png'` 行。

- [ ] **Step 11.3：check-ts + 测试**

```bash
yarn check-ts 2>&1 | tail -3
yarn test 2>&1 | grep -E 'Test Files|Tests' | tail -3
```

Expected: 0 ts errors / 0 测试回归.

- [ ] **Step 11.4：Commit（仅在有改动时）**

如本步未改任何代码，跳过 commit，直接进 Task 12.

```bash
git status -s
# 如有改动
git add veriguard-front/src/components/nodes/NodeAttackChainNode.tsx 2>/dev/null
git commit -m "$(cat <<'EOF'
执行：邮件 inject 节点画布图标 mapping（B-ii PR-B Step 11）

EmailContract 已声明颜色 #1976d2 + /img/icon-email.png，前端节点渲染识别新 inject 类型.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12：PR-B 验证 + push + 创建 PR

### Steps

- [ ] **Step 12.1：跑 PR-B 全套后端测试**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-b-mail-inject
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am test -Dtest='SmtpProfileServiceTest,MailInjectorTest,LinkExpectationServiceTest,AgentServiceSelectByCapabilityTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | tail
```

Expected: 全 BUILD SUCCESS / 7 + 6 + 18 + 8 = 39 测试过.

- [ ] **Step 12.2：跑前端 check-ts + test**

```bash
cd veriguard-front
yarn check-ts 2>&1 | tail -3
yarn test 2>&1 | grep -E 'Test Files|Tests' | tail -3
```

Expected: 0 ts errors / 0 回归.

- [ ] **Step 12.3：master 锁验证**

```bash
git -C /Users/lamba/github/Veriguard ls-remote origin master | head -1
```

Expected: `5d7e05da627639491274175d2a77cf95b2c0fe7d	refs/heads/master`.

- [ ] **Step 12.4：push branch**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-b-mail-inject
git push -u origin feat/b-ii-pr-b-mail-inject 2>&1 | tail -3
```

- [ ] **Step 12.5：创建 PR**

```bash
gh pr create --base main --title "二开 B-ii PR-B: 邮件 Inject + SMTP Profile 管理" --body "$(cat <<'EOF'
## Summary

B-ii sub-project 第 2 个 PR（独立，可与 PR-C/PR-D 并行）：落地邮件 inject 类型，覆盖 PRD §2.3 第 3 行 6 类自定义用例中的"配置邮件形式"。

设计稿：\`docs/superpowers/specs/2026-05-12-veriguard-b-ii-design.md\`（main 已合并）
实施计划：\`docs/superpowers/plans/2026-05-12-veriguard-b-ii-pr-b-mail-inject-plan.md\`

## 改动概览

11 个 commit（Step 1-11）：

- **Step 1** V8 Flyway: smtp_profiles 表
- **Step 2** SmtpProfile entity + Repository
- **Step 3** SmtpProfileService + 7 单测（TDD）
- **Step 4** SmtpProfile REST API CRUD
- **Step 5** EmailContent 数据模型
- **Step 6** MailInjector 服务 + 6 单测（TDD）
- **Step 7** EmailContract Contractor 注册
- **Step 8** EmailExecutor 实现
- **Step 9** EmailNodeExecutorIntegration + Factory
- **Step 10** 前端 SMTP Profile 管理界面（列表 + 表单）
- **Step 11** 邮件 inject 节点画布图标 mapping

## 设计要点

- MailInjector 用 jakarta.mail 直接构造 Session/Transport，独立于现有 SmtpService（后者绑定单一全局 SMTP 用于平台通知，不适合多 profile 场景）
- 邮件 inject 由平台后端直发 SMTP，不通过 Agent（spec §2 决策点）— 真实钓鱼场景就是"外部攻击者 → 客户邮件网关"
- Integration 模式参考 ManualNodeExecutorIntegration（已合并）
- 安全：SMTP 凭据加密由 Veriguard 现有数据加密层处理；高危发件操作记入审计日志

## Test plan

- [x] SmtpProfileServiceTest 7 passed
- [x] MailInjectorTest 6 passed
- [x] LinkExpectationServiceTest 18 baseline passed（0 回归）
- [x] AgentServiceSelectByCapabilityTest 8 baseline passed（0 回归）
- [x] yarn check-ts 0 errors
- [x] yarn test 0 回归
- [x] origin/master 仍锁 \`5d7e05da6\`

## 范围 boundary（spec §12.1）

不做：邮件接收验证（IMAP/Exchange API）/ 邮件追踪像素 / 附件杀软联动 / 多 SMTP 负载均衡 / 失败回退。

## 后续

PR-B merged main 后启动 PR-C（Web 攻击包 inject + Agent HTTP 能力）writing-plans。
EOF
)" 2>&1 | tail -3
```

- [ ] **Step 12.6：等用户 review/merge**

PR-B merged main 后进入 PR-C writing-plans。

---

## Summary

11 个 commit / 12 个 task / 1 个 worktree / 1 个 PR：

| Task | 改动文件 | 测试 |
|---|---|---|
| 1 | V8 migration | LinkExpectationServiceTest 校验 Flyway |
| 2 | SmtpProfile entity + repo | compile 校验 |
| 3 | SmtpProfileService + 7 单测 | TDD 7 测试 |
| 4 | SmtpProfileApi + Input DTO | compile + baseline |
| 5 | EmailContent 数据模型 | compile |
| 6 | MailInjector + 6 单测 | TDD 6 测试 |
| 7 | EmailContract | compile |
| 8 | EmailExecutor | compile + baseline |
| 9 | EmailNodeExecutorIntegration + Factory | compile + baseline |
| 10 | 前端 SMTP profile 管理 | check-ts + test |
| 11 | 节点画布图标（可选）| check-ts + test |
| 12 | 验证 + push + PR | 全套测试 + master 锁 |

## Test plan

- [ ] mvn 后端 BUILD SUCCESS
- [ ] SmtpProfileServiceTest 7 passed
- [ ] MailInjectorTest 6 passed
- [ ] LinkExpectationServiceTest 18 passed（baseline，0 回归）
- [ ] AgentServiceSelectByCapabilityTest 8 passed（baseline，0 回归）
- [ ] yarn check-ts 0 errors
- [ ] yarn test 0 回归
- [ ] origin/master 仍锁 `5d7e05da6`
- [ ] PR base=main，标题含 "B-ii PR-B"

## 范围 boundary（spec §12.1 — 本 plan 不做）

- ❌ 邮件接收验证（IMAP / Exchange API）
- ❌ 邮件追踪像素 / 阅读回执
- ❌ 附件杀软 sandbox 联动
- ❌ 多 SMTP 负载均衡 / 失败回退
- ❌ SMTP 凭据加密的密钥管理改造（沿用 Veriguard 既有加密层）
- ❌ Agent 客户端代码改动（邮件 inject 由平台后端直发）

## 自审 checklist

完成所有 task 后做最后一遍：

- [ ] V8 Flyway 正确加 smtp_profiles 表，应用启动不报错
- [ ] SmtpProfile entity 字段与 V8 列一一对应
- [ ] SmtpProfileService 单测 7/7 通过
- [ ] SmtpProfileApi 5 个端点（GET list / GET id / POST / PUT / DELETE）齐全
- [ ] EmailContent JSON 字段 (smtp_profile_id, subject, body_text, body_html, from_alias, attachments, inline_links, expectations) 完整
- [ ] MailInjector 不依赖 SmtpService（独立 Session 构造）
- [ ] EmailContract 注册的字段与 EmailContent 字段对齐
- [ ] EmailExecutor 处理 SMTP profile 缺失 / 收件人为空等错误路径
- [ ] EmailNodeExecutorIntegration 用固定 EMAIL_INJECTOR_ID
- [ ] 前端 SMTP profile CRUD 流程完整
- [ ] 11 个 commit 全部含 `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`
- [ ] master 锁 `5d7e05da6` 不变
